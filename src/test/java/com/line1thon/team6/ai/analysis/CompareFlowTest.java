package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.entity.AiCallResult;
import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.entity.AiUsageLog;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.repository.AiUsageLogRepository;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 생각 제출 → 커밋 후 비동기 비교 분석 → 결과 저장과 잠금 해제까지 실제 흐름.
 * AI 제공사만 목으로 바꾸고, 리스너·비동기 실행·검증·재호출·사용 기록·LockService는 실제 빈을 쓴다.
 */
@SpringBootTest(properties = "deveil.ai.listener-enabled=true")
@AutoConfigureMockMvc
class CompareFlowTest {

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";

    private static final String VALID_OUTPUT = """
            {"common": [{"title": "초기값 확인", "user_quote": "첫 렌더링 때 users 값부터 볼게요.", "ai_view": "초기 상태가 원인", "reason": "같은 지점"}],
             "supplement": [{"title": "방어 코드", "user_quote": "", "ai_view": "users?.map", "reason": "놓친 부분"}],
             "unique": [], "error": [], "judgment_hold": []}
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    AiUsageLogRepository usageLogRepository;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    LlmClient llmClient;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
        given(llmClient.model()).willReturn("test-model");
    }

    /** 선생성이 끝난 채팅 (이벤트 없이 저장해서 선생성 리스너는 돌지 않는다) */
    private Long doneChat() {
        Long chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", "users.map(u => u.name)", false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변 전문");
        return chatId;
    }

    /** 생각을 제출하고 [jobId, requestId]를 돌려준다 */
    private Submitted submit(Long chatId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chats/{chatId}/thoughts", chatId)
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"%s\", \"selectedChoice\": 0}".formatted(THOUGHT)))
                .andExpect(status().isAccepted())
                .andReturn();
        Long jobId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asLong();
        return new Submitted(chatId, jobId, result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    private record Submitted(Long chatId, Long jobId, String requestId) {
    }

    /** 백그라운드 분석이 끝날 때까지(PENDING이 아니게 될 때까지) 최대 5초 기다린다 */
    private AnalysisJob awaitJob(Long jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() != AnalysisStatus.PENDING) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("분석이 5초 안에 끝나지 않음: jobId=" + jobId);
    }

    private List<AiUsageLog> awaitUsageLogs(String requestId, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        List<AiUsageLog> logs = List.of();
        while (System.currentTimeMillis() < deadline) {
            logs = usageLogRepository.findByRequestIdOrderByIdAsc(requestId);
            if (logs.size() >= expected) {
                return logs;
            }
            Thread.sleep(50);
        }
        return logs;
    }

    private ChatStatus chatStatusOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getStatus();
    }

    @Test
    void 생각을_제출하면_백그라운드에서_비교하고_결과를_저장한_뒤_잠금을_푼다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 300, 500, "STOP"));
        Submitted submitted = submit(doneChat());

        AnalysisJob job = awaitJob(submitted.jobId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getErrorCode()).isNull();
        assertThat(job.getFindings()).extracting(f -> f.get("category")).containsExactly("COMMON", "COMPLEMENT");
        assertThat(job.getFindings().getFirst()).containsAllEntriesOf(Map.of(
                "category", "COMMON", "title", "초기값 확인", "userQuote", "첫 렌더링 때 users 값부터 볼게요.",
                "aiPerspective", "초기 상태가 원인", "detail", "같은 지점"));
        assertThat(job.getFindings().getFirst()).containsEntry("followUpQuestion", null);
        assertThat(chatStatusOf(submitted.chatId())).isEqualTo(ChatStatus.UNLOCKED_SELF);

        assertThat(awaitUsageLogs(submitted.requestId(), 1)).singleElement().satisfies(log -> {
            assertThat(log.getCallType()).isEqualTo(AiCallType.COMPARE);
            assertThat(log.getPromptVersion()).isEqualTo(ComparePrompt.VERSION);
            assertThat(log.getGuestSessionId()).isEqualTo(session.getId());
            assertThat(log.getResult()).isEqualTo(AiCallResult.SUCCESS);
        });
    }

    @Test
    void 분석이_끝나면_채팅_상세에_DONE과_잠금_해제가_보인다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        Submitted submitted = submit(doneChat());
        awaitJob(submitted.jobId());

        mockMvc.perform(get("/api/chats/{chatId}", submitted.chatId()).header("X-Guest-Token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNLOCKED_SELF"))
                .andExpect(jsonPath("$.analysis.jobId").value(submitted.jobId()))
                .andExpect(jsonPath("$.analysis.status").value("DONE"));
    }

    @Test
    void AI에는_질문_코드_기준_답변_생각을_보낸다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        awaitJob(submit(doneChat()).jobId());

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.callType()).isEqualTo(AiCallType.COMPARE);
        assertThat(request.maxOutputTokens()).isEqualTo(2500);
        assertThat(request.messages().getFirst().text())
                .contains("<USER_QUESTION>\nusers가 undefined라고 나와요\n</USER_QUESTION>")
                .contains("<USER_CODE>\nusers.map(u => u.name)\n</USER_CODE>")
                .contains("<REFERENCE_ANSWER>\n기준 답변 전문\n</REFERENCE_ANSWER>")
                .contains("<USER_THOUGHT>\n" + THOUGHT + "\n</USER_THOUGHT>");
    }

    @Test
    void 인용이_모두_틀리면_다시_불러서_성공한다() throws Exception {
        given(llmClient.generate(any()))
                .willReturn(new LlmResponse("""
                        {"common": [{"title": "t", "user_quote": "지어낸 문장", "ai_view": "v", "reason": "r"}],
                         "supplement": [], "unique": [], "error": [], "judgment_hold": []}
                        """, 1, 1, "STOP"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        Submitted submitted = submit(doneChat());

        assertThat(awaitJob(submitted.jobId()).getStatus()).isEqualTo(AnalysisStatus.DONE);
        verify(llmClient, times(2)).generate(any());
    }

    @Test
    void 세_번_모두_형식이_틀리면_FAILED와_AI_INVALID_JSON이고_잠금은_그대로다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse("not json", 1, 1, "STOP"));
        Submitted submitted = submit(doneChat());

        AnalysisJob job = awaitJob(submitted.jobId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("AI_INVALID_JSON");
        assertThat(job.getFindings()).isNull();
        assertThat(chatStatusOf(submitted.chatId())).isEqualTo(ChatStatus.LOCKED);
        verify(llmClient, times(3)).generate(any());
    }

    @Test
    void 시간_초과면_다시_부르지_않고_FAILED와_AI_TIMEOUT() throws Exception {
        given(llmClient.generate(any())).willThrow(new AiException(AiErrorCode.AI_TIMEOUT, "timeout"));
        Submitted submitted = submit(doneChat());

        AnalysisJob job = awaitJob(submitted.jobId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(chatStatusOf(submitted.chatId())).isEqualTo(ChatStatus.LOCKED);
        verify(llmClient, times(1)).generate(any());
    }

    @Test
    void 실패한_뒤_재시도하면_백그라운드에서_다시_분석해_DONE이_된다() throws Exception {
        given(llmClient.generate(any()))
                .willThrow(new AiException(AiErrorCode.AI_API_ERROR, "429"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        Submitted submitted = submit(doneChat());
        assertThat(awaitJob(submitted.jobId()).getStatus()).isEqualTo(AnalysisStatus.FAILED);

        mockMvc.perform(post("/api/chats/{chatId}/analysis/retry", submitted.chatId()).header("X-Guest-Token", token))
                .andExpect(status().isAccepted());

        AnalysisJob job = awaitJob(submitted.jobId());
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getErrorCode()).isNull();
        assertThat(chatStatusOf(submitted.chatId())).isEqualTo(ChatStatus.UNLOCKED_SELF);
        verify(llmClient, times(2)).generate(any());
    }

    @Test
    void 예상하지_못한_오류여도_PENDING에_멈추지_않고_AI_API_ERROR로_FAILED() throws Exception {
        given(llmClient.generate(any())).willThrow(new IllegalStateException("bug"));
        Submitted submitted = submit(doneChat());

        AnalysisJob job = awaitJob(submitted.jobId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job.getErrorCode()).isEqualTo("AI_API_ERROR");
    }
}
