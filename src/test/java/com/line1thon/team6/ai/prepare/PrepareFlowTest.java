package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.entity.AiCallResult;
import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.entity.AiUsageLog;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.repository.AiUsageLogRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 질문 생성 → 커밋 후 비동기 선생성 → chat 저장까지 실제 흐름.
 * AI 제공사만 목으로 바꾸고, 리스너·비동기 실행·검증·재호출·사용 기록은 실제 빈을 쓴다.
 */
@SpringBootTest(properties = "deveil.ai.listener-enabled=true")
@AutoConfigureMockMvc
class PrepareFlowTest {

    private static final String VALID_OUTPUT = """
            {"choices": ["칩1", "칩2", "칩3"], "hints": ["힌트1", "힌트2"], "reference_answer": "기준 답변"}
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

    /** 질문을 만들고 [chatId, requestId]를 돌려준다 */
    private Created createChat() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/chats")
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientRequestId": "%s", "type": "DEBUGGING", "question": "왜 undefined인가요?",
                                 "code": "const key = 'sk-abcdefghijklmnopqrstuvwxyz123456';"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn();
        Long chatId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("chatId").asLong();
        return new Created(chatId, result.getResponse().getHeader(RequestIdFilter.HEADER));
    }

    private record Created(Long chatId, String requestId) {
    }

    /** 백그라운드 선생성이 끝날 때까지(PREPARING이 아니게 될 때까지) 최대 5초 기다린다 */
    private Chat awaitPrepared(Long chatId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            Chat chat = chatRepository.findById(chatId).orElseThrow();
            if (chat.getPrepareStatus() != PrepareStatus.PREPARING) {
                return chat;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("선생성이 5초 안에 끝나지 않음: chatId=" + chatId);
    }

    /** 사용 기록은 상태 변경 직전·직후에 저장되므로 개수가 찰 때까지 잠깐 기다린다 */
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

    @Test
    void 질문을_만들면_백그라운드에서_칩_힌트_기준_답변을_저장하고_DONE이_된다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 100, 200, "STOP"));

        Created created = createChat();
        Chat chat = awaitPrepared(created.chatId());

        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
        assertThat(chat.getChoices()).containsExactly("칩1", "칩2", "칩3");
        assertThat(chat.getHints()).containsExactly("힌트1", "힌트2");
        assertThat(chat.getReferenceAnswer()).isEqualTo("기준 답변");

        List<AiUsageLog> logs = awaitUsageLogs(created.requestId(), 1);
        assertThat(logs).singleElement().satisfies(log -> {
            assertThat(log.getGuestSessionId()).isEqualTo(session.getId());
            assertThat(log.getCallType()).isEqualTo(AiCallType.PREPARE);
            assertThat(log.getModel()).isEqualTo("test-model");
            assertThat(log.getPromptVersion()).isEqualTo(PreparePrompt.VERSION);
            assertThat(log.getInputTokens()).isEqualTo(100);
            assertThat(log.getOutputTokens()).isEqualTo(200);
            assertThat(log.getResult()).isEqualTo(AiCallResult.SUCCESS);
            assertThat(log.getErrorCode()).isNull();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void 칩이_1개나_2개여도_선생성이_완료되고_상세_조회에_그대로_반환한다(int count) throws Exception {
        List<String> choices = List.of("인덱스 범위", "반복 횟수").subList(0, count);
        String output = jsonMapper.writeValueAsString(java.util.Map.of(
                "choices", choices, "hints", List.of("힌트1", "힌트2"), "reference_answer", "기준 답변"));
        given(llmClient.generate(any())).willReturn(new LlmResponse(output, 1, 1, "STOP"));

        Created created = createChat();
        Chat chat = awaitPrepared(created.chatId());
        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
        assertThat(chat.getChoices()).containsExactlyElementsOf(choices);
        String response = mockMvc.perform(get("/api/chats/{chatId}", created.chatId())
                        .header("X-Guest-Token", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jsonMapper.readTree(response).get("choices"))
                .isEqualTo(jsonMapper.valueToTree(choices));
    }

    @Test
    void AI에는_비밀값을_가린_코드와_질문을_보낸다() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));

        awaitPrepared(createChat().chatId());

        var captor = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
        verify(llmClient).generate(captor.capture());
        String userPrompt = captor.getValue().messages().getFirst().text();
        assertThat(userPrompt).contains("왜 undefined인가요?").contains("[REDACTED]")
                .doesNotContain("sk-abcdefghijklmnopqrstuvwxyz123456");
        assertThat(captor.getValue().callType()).isEqualTo(AiCallType.PREPARE);
    }

    @Test
    void 형식이_틀리면_다시_불러서_성공한다() throws Exception {
        given(llmClient.generate(any()))
                .willReturn(new LlmResponse("{\"choices\": [\"하나\"]}", 1, 1, "STOP"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));

        Created created = createChat();

        assertThat(awaitPrepared(created.chatId()).getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
        verify(llmClient, times(2)).generate(any());
        assertThat(awaitUsageLogs(created.requestId(), 2))
                .extracting(AiUsageLog::getResult, AiUsageLog::getErrorCode)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(AiCallResult.FAILED, AiErrorCode.AI_INVALID_JSON),
                        org.assertj.core.groups.Tuple.tuple(AiCallResult.SUCCESS, null));
    }

    @Test
    void 세_번_모두_형식이_틀리면_FAILED() throws Exception {
        given(llmClient.generate(any())).willReturn(new LlmResponse("not json", 1, 1, "STOP"));

        Created created = createChat();

        assertThat(awaitPrepared(created.chatId()).getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);
        verify(llmClient, times(3)).generate(any());
    }

    @Test
    void 시간_초과면_다시_부르지_않고_FAILED로_바꾸고_오류_코드를_기록한다() throws Exception {
        given(llmClient.generate(any())).willThrow(new AiException(AiErrorCode.AI_TIMEOUT, "timeout"));

        Created created = createChat();

        assertThat(awaitPrepared(created.chatId()).getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);
        verify(llmClient, times(1)).generate(any());
        assertThat(awaitUsageLogs(created.requestId(), 1)).singleElement().satisfies(log -> {
            assertThat(log.getResult()).isEqualTo(AiCallResult.FAILED);
            assertThat(log.getErrorCode()).isEqualTo(AiErrorCode.AI_TIMEOUT);
            assertThat(log.getInputTokens()).isNull();
        });
    }

    @Test
    void 예상하지_못한_오류여도_PREPARING에_멈추지_않고_FAILED가_된다() throws Exception {
        given(llmClient.generate(any())).willThrow(new IllegalStateException("bug"));

        Created created = createChat();

        assertThat(awaitPrepared(created.chatId()).getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);
    }

    @Test
    void 실패한_뒤_재시도하면_다시_선생성한다() throws Exception {
        given(llmClient.generate(any()))
                .willThrow(new AiException(AiErrorCode.AI_API_ERROR, "429"))
                .willReturn(new LlmResponse(VALID_OUTPUT, 1, 1, "STOP"));
        Long chatId = createChat().chatId();
        assertThat(awaitPrepared(chatId).getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);

        mockMvc.perform(post("/api/chats/{chatId}/prepare/retry", chatId).header("X-Guest-Token", token))
                .andExpect(status().isAccepted());

        assertThat(awaitPrepared(chatId).getPrepareStatus()).isEqualTo(PrepareStatus.DONE);
    }
}
