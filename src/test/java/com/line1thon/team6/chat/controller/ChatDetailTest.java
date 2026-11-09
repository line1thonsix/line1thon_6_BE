package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/chats/{chatId}: 새로고침 복구와 선생성 폴링 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatDetailTest {

    private static final List<String> CHOICES = List.of("칩1", "칩2", "칩3");
    private static final List<String> HINTS = List.of("힌트1", "힌트2");
    private static final String REFERENCE_ANSWER = "기준 답변";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    /** PREPARING 상태의 채팅 하나 */
    private Long newChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", "const [users] = useState();", false)).getId();
    }

    private Long doneChat() {
        Long chatId = newChat(session);
        chatService.completePrepare(chatId, CHOICES, HINTS, REFERENCE_ANSWER);
        return chatId;
    }

    /** 힌트 열기 API는 아직 없어서 DB 값을 직접 바꾼다 */
    private void setHintLevel(Long chatId, int hintLevel) {
        jdbcTemplate.update("update chat set hint_level = ? where id = ?", hintLevel, chatId);
    }

    private ResultActions getDetail(Long chatId) throws Exception {
        return mockMvc.perform(get("/api/chats/{chatId}", chatId).header("X-Guest-Token", token));
    }

    @Test
    void 선생성_중이면_PREPARING과_빈_칩_힌트를_돌려준다() throws Exception {
        Long chatId = newChat(session);

        getDetail(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chatId").value(chatId))
                .andExpect(jsonPath("$.type").value("DEBUGGING"))
                .andExpect(jsonPath("$.question").value("users가 undefined라고 나와요"))
                .andExpect(jsonPath("$.code").value("const [users] = useState();"))
                .andExpect(jsonPath("$.secretMasked").value(false))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.prepareStatus").value("PREPARING"))
                .andExpect(jsonPath("$.choices").isEmpty())
                .andExpect(jsonPath("$.hintLevel").value(0))
                .andExpect(jsonPath("$.hints", hasSize(0)))
                .andExpect(jsonPath("$.hasNextHint").value(true))
                .andExpect(jsonPath("$.basicModeAvailable").value(false))
                .andExpect(jsonPath("$.thought").isEmpty())
                .andExpect(jsonPath("$.selectedChoice").isEmpty())
                .andExpect(jsonPath("$.hintUsed").value(false))
                .andExpect(jsonPath("$.analysis").isEmpty())
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(
                        "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00$")));
    }

    @Test
    void 선생성이_끝나면_칩을_돌려주고_기준_답변은_넣지_않는다() throws Exception {
        Long chatId = doneChat();

        getDetail(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepareStatus").value("DONE"))
                .andExpect(jsonPath("$.choices", hasSize(3)))
                .andExpect(jsonPath("$.choices[0]").value("칩1"))
                .andExpect(jsonPath("$.hints", hasSize(0)))
                .andExpect(jsonPath("$.referenceAnswer").doesNotExist());
    }

    @Test
    void 힌트는_공개된_단계까지만_돌려준다() throws Exception {
        Long chatId = doneChat();
        setHintLevel(chatId, 1);

        getDetail(chatId)
                .andExpect(jsonPath("$.hintLevel").value(1))
                .andExpect(jsonPath("$.hints", hasSize(1)))
                .andExpect(jsonPath("$.hints[0].level").value(1))
                .andExpect(jsonPath("$.hints[0].text").value("힌트1"))
                .andExpect(jsonPath("$.hasNextHint").value(true))
                .andExpect(jsonPath("$.basicModeAvailable").value(false));
    }

    @Test
    void 힌트_2단계면_다음_힌트가_없고_기초_학습이_가능하다() throws Exception {
        Long chatId = doneChat();
        setHintLevel(chatId, 2);

        getDetail(chatId)
                .andExpect(jsonPath("$.hints", hasSize(2)))
                .andExpect(jsonPath("$.hints[1].level").value(2))
                .andExpect(jsonPath("$.hints[1].text").value("힌트2"))
                .andExpect(jsonPath("$.hasNextHint").value(false))
                .andExpect(jsonPath("$.basicModeAvailable").value(true));
    }

    @Test
    void 선생성이_실패하면_FAILED와_빈_칩을_돌려준다() throws Exception {
        Long chatId = newChat(session);
        chatService.failPrepare(chatId);

        getDetail(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepareStatus").value("FAILED"))
                .andExpect(jsonPath("$.choices").isEmpty());
    }

    @Test
    void 남의_대화는_404() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChatId = newChat(other);

        getDetail(othersChatId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void 없는_대화는_404() throws Exception {
        getDetail(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        Long chatId = newChat(session);

        mockMvc.perform(get("/api/chats/{chatId}", chatId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
