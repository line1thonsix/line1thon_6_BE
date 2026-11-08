package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 DB(H2)까지 거치는 통합 테스트. 테스트마다 새 게스트 세션을 써서 서로 영향을 주지 않는다 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ChatControllerTest {

    private static final String KOREA_TIME_PATTERN =
            "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00$";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ApplicationEvents events;

    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        guestSessionRepository.save(new GuestSession(token));
    }

    private ResultActions postChat(String body) throws Exception {
        return mockMvc.perform(post("/api/chats")
                .header("X-Guest-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static String body(String clientRequestId, String type, String question, String code) {
        return """
                {"clientRequestId": "%s", "type": "%s", "question": "%s", "code": %s}
                """.formatted(clientRequestId, type, question, code == null ? "null" : "\"" + code + "\"");
    }

    @Test
    void 질문을_저장하고_201과_PREPARING_상태를_돌려준다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();

        postChat(body(clientRequestId, "DEBUGGING", "users가 undefined라고 나와요", "const [users] = useState();"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chatId").isNumber())
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.type").value("DEBUGGING"))
                .andExpect(jsonPath("$.prepareStatus").value("PREPARING"))
                .andExpect(jsonPath("$.secretMasked").value(false))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(KOREA_TIME_PATTERN)))
                .andExpect(jsonPath("$.referenceAnswer").doesNotExist())
                .andExpect(jsonPath("$.choices").doesNotExist())
                .andExpect(jsonPath("$.hints").doesNotExist());

        Chat saved = chatRepository.findAll().stream()
                .filter(c -> c.getClientRequestId().equals(clientRequestId))
                .findFirst().orElseThrow();
        assertThat(saved.getHintLevel()).isZero();
        assertThat(saved.getPrepareStatus()).isEqualTo(PrepareStatus.PREPARING);
        assertThat(saved.getPrepareStartedAt()).isNotNull();
    }

    @Test
    void 저장하면_선생성_이벤트를_발행한다() throws Exception {
        String response = postChat(body(UUID.randomUUID().toString(), "CONCEPT", "클로저가 뭐예요?", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long chatId = Long.parseLong(response.replaceAll(".*\"chatId\":(\\d+).*", "$1"));

        assertThat(events.stream(PrepareRequested.class))
                .extracting(PrepareRequested::chatId)
                .containsExactly(chatId);
    }

    @Test
    void 같은_전송_ID로_다시_보내면_새로_만들지_않고_200으로_기존_대화를_돌려준다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        String first = postChat(body(clientRequestId, "DESIGN", "어떤 구조가 좋을까요?", null))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        events.clear();

        String second = postChat(body(clientRequestId, "DESIGN", "어떤 구조가 좋을까요?", null))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(events.stream(PrepareRequested.class)).isEmpty();
    }

    @Test
    void 다른_세션은_같은_전송_ID를_써도_따로_만든다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        postChat(body(clientRequestId, "DESIGN", "질문", null)).andExpect(status().isCreated());

        token = UUID.randomUUID().toString();
        guestSessionRepository.save(new GuestSession(token));
        postChat(body(clientRequestId, "DESIGN", "질문", null)).andExpect(status().isCreated());
    }

    @Test
    void 코드의_비밀값은_가려서_저장하고_secretMasked를_true로_돌려준다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        postChat(body(clientRequestId, "DEBUGGING", "연결이 안 돼요", "const apiKey = 'sk-abcdefghijklmnopqrstuvwx';"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretMasked").value(true));

        Chat saved = chatRepository.findAll().stream()
                .filter(c -> c.getClientRequestId().equals(clientRequestId))
                .findFirst().orElseThrow();
        assertThat(saved.getCode()).isEqualTo("const apiKey = '[REDACTED]';").doesNotContain("sk-abc");
    }

    @Test
    void 질문_앞뒤_공백은_지우고_저장한다() throws Exception {
        String clientRequestId = UUID.randomUUID().toString();
        postChat(body(clientRequestId, "CONCEPT", "   클로저가 뭐예요?   ", "   "))
                .andExpect(status().isCreated());

        Chat saved = chatRepository.findAll().stream()
                .filter(c -> c.getClientRequestId().equals(clientRequestId))
                .findFirst().orElseThrow();
        assertThat(saved.getQuestion()).isEqualTo("클로저가 뭐예요?");
        assertThat(saved.getCode()).isNull();
    }

    @Test
    void 질문이_공백뿐이면_400() throws Exception {
        postChat(body(UUID.randomUUID().toString(), "CONCEPT", "   ", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("질문을 입력해 주세요."));
    }

    @Test
    void 질문이_2000자를_넘으면_400() throws Exception {
        postChat(body(UUID.randomUUID().toString(), "CONCEPT", "가".repeat(2001), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 질문이_딱_2000자면_저장한다() throws Exception {
        postChat(body(UUID.randomUUID().toString(), "CONCEPT", "가".repeat(2000), null))
                .andExpect(status().isCreated());
    }

    @Test
    void 코드가_6000자를_넘으면_400() throws Exception {
        postChat(body(UUID.randomUUID().toString(), "DEBUGGING", "질문", "a".repeat(6001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void FREE_유형은_400() throws Exception {
        postChat(body(UUID.randomUUID().toString(), "FREE", "질문", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 전송_ID가_UUID가_아니면_400() throws Exception {
        postChat(body("not-a-uuid", "CONCEPT", "질문", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 전송_ID가_없으면_400() throws Exception {
        postChat("""
                {"type": "CONCEPT", "question": "질문"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 예시_질문_값이_목록에_없으면_400() throws Exception {
        postChat("""
                {"clientRequestId": "%s", "type": "CONCEPT", "question": "질문", "exampleId": "EXAMPLE_UNKNOWN"}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/chats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "CONCEPT", "질문", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
