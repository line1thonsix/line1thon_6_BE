package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.entity.AiCallResult;
import com.line1thon.team6.ai.entity.AiCallType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 힌트 2단계 → 기초 학습 → 커밋 후 비동기 기초 설명 → 결과 저장과 잠금 해제(UNLOCKED_ASSISTED)까지 실제 흐름.
 * AI 제공사만 목으로 바꾼다.
 */
@SpringBootTest(properties = "deveil.ai.listener-enabled=true")
@AutoConfigureMockMvc
class BasicFlowTest {

    private static final String VALID_OUTPUT = """
            {"summary": "요약", "example_code": "const a = 1;", "observe": "관찰", "change": "변경"}
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

    /** 선생성이 끝나고 힌트 2단계를 API로 연 채팅 */
    private Long chatWithAllHints() throws Exception {
        Long chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", "users.map(u => u.name)", false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변 전문");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/chats/{chatId}/hints", chatId).header("X-Guest-Token", token))
                    .andExpect(status().isOk());
        }
        return chatId;
    }

    private record Started(Long chatId, Long jobId, String requestId) {
    }

    private Started startBasic(Long chatId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chats/{chatId}/basic-mode", chatId).header("X-Guest-Token", token))
                .andExpect(status().isAccepted())
                .andReturn();
        Long jobId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asLong();
        return new Started(chatId, jobId, result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    private AnalysisJob awaitJob(Long jobId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
            if (job.getStatus() != AnalysisStatus.PENDING) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("기초 설명이 5초 안에 끝나지 않음: jobId=" + jobId);
    }

    private ChatStatus chatStatusOf(Long chatId) {
        return chatRepository.findById(chatId).orElseThrow().getStatus();
    }

    @Test
    void 기초_학습을_시작하면_백그라운드에서_설명을_만들어_저장하고_ASSISTED로_잠금을_푼다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 200, 300, "STOP"));
        Started started = startBasic(chatWithAllHints());

        AnalysisJob job = awaitJob(started.jobId());

        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(jsonMapper.readValue(job.getBasicExplanation(), BasicExplanation.class))
                .isEqualTo(new BasicExplanation("요약", "const a = 1;", "관찰", "변경"));
        assertThat(job.getFindings()).isNull();
        assertThat(chatStatusOf(started.chatId())).isEqualTo(ChatStatus.UNLOCKED_ASSISTED);

        long deadline = System.currentTimeMillis() + 5_000;
        while (usageLogRepository.findByRequestIdOrderByIdAsc(started.requestId()).isEmpty()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertThat(usageLogRepository.findByRequestIdOrderByIdAsc(started.requestId())).singleElement()
                .satisfies(log -> {
                    assertThat(log.getCallType()).isEqualTo(AiCallType.BASIC);
                    assertThat(log.getPromptVersion()).isEqualTo(BasicPrompt.VERSION);
                    assertThat(log.getResult()).isEqualTo(AiCallResult.SUCCESS);
                });
    }

    @Test
    void AI에는_질문과_코드만_보내고_기준_답변과_생각은_없다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        awaitJob(startBasic(chatWithAllHints()).jobId());

        ArgumentCaptor<LlmRequest> captor = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(captor.capture());
        LlmRequest request = captor.getValue();
        assertThat(request.callType()).isEqualTo(AiCallType.BASIC);
        assertThat(request.maxOutputTokens()).isEqualTo(1500);
        assertThat(request.messages().getFirst().text())
                .contains("<USER_QUESTION>\nusers가 undefined라고 나와요\n</USER_QUESTION>")
                .contains("<USER_CODE>\nusers.map(u => u.name)\n</USER_CODE>")
                .doesNotContain("기준 답변 전문")
                .doesNotContain("<USER_THOUGHT>", "<REFERENCE_ANSWER>");
    }

    @Test
    void 형식이_틀리면_다시_불러서_성공한다() throws Exception {
        given(llmClient.generate(any()))
                .willReturn(new LlmResponse("{\"summary\": \"요약\"}", 1, 1, "STOP"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));

        assertThat(awaitJob(startBasic(chatWithAllHints()).jobId()).getStatus()).isEqualTo(AnalysisStatus.DONE);
        verify(llmClient, times(2)).generate(any());
    }

    @Test
    void 실패하면_FAILED이고_재시도하면_다시_만들어_DONE이_된다() throws Exception {
        given(llmClient.generate(any()))
                .willThrow(new AiException(AiErrorCode.AI_TIMEOUT, "timeout"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        Started started = startBasic(chatWithAllHints());

        AnalysisJob failed = awaitJob(started.jobId());
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(chatStatusOf(started.chatId())).isEqualTo(ChatStatus.LOCKED);

        mockMvc.perform(post("/api/chats/{chatId}/analysis/retry", started.chatId()).header("X-Guest-Token", token))
                .andExpect(status().isAccepted());

        assertThat(awaitJob(started.jobId()).getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(chatStatusOf(started.chatId())).isEqualTo(ChatStatus.UNLOCKED_ASSISTED);
    }
}
