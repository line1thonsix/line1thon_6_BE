package com.line1thon.team6.chat.controller;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /api/chats/{chatId}/thoughts: 검증 → 비교 분석 작업 시작 → 최초 작성본 저장 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class ThoughtSubmitTest {

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ThoughtRepository thoughtRepository;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationEvents events;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long preparingChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", null, false)).getId();
    }

    private Long doneChat() {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        return chatId;
    }

    private static String body(String content, Integer selectedChoice) {
        return """
                {"content": %s, "selectedChoice": %s}
                """.formatted(content == null ? "null" : "\"" + content.replace("\n", "\\n") + "\"", selectedChoice);
    }

    private ResultActions submit(Long chatId, String body) throws Exception {
        return mockMvc.perform(post("/api/chats/{chatId}/thoughts", chatId)
                .header("X-Guest-Token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Thought thoughtOf(Long chatId) {
        return thoughtRepository.findAll().stream()
                .filter(t -> t.getChat().getId().equals(chatId))
                .findFirst().orElseThrow();
    }

    // ===== 성공 =====

    @Test
    void 제출하면_202와_PENDING_비교_분석_작업을_돌려준다() throws Exception {
        Long chatId = doneChat();

        submit(chatId, body(THOUGHT, 0))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNumber())
                .andExpect(jsonPath("$.mode").value("COMPARE"))
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.hintUsed").value(false));

        AnalysisJob job = analysisJobRepository.findByChatId(chatId).orElseThrow();
        assertThat(job.getMode()).isEqualTo(AnalysisMode.COMPARE);
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(events.stream(AnalysisRequested.class))
                .containsExactly(new AnalysisRequested(chatId, job.getId()));
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.LOCKED);
    }

    @Test
    void 최초_작성본을_앞뒤_공백을_빼고_저장한다() throws Exception {
        Long chatId = doneChat();

        submit(chatId, body("  " + THOUGHT + "\n", 2)).andExpect(status().isAccepted());

        Thought thought = thoughtOf(chatId);
        assertThat(thought.getContent()).isEqualTo(THOUGHT);
        assertThat(thought.getSelectedChoice()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void 칩이_적어도_실제_마지막_칩을_선택해_제출할_수_있다(int count) throws Exception {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2").subList(0, count),
                List.of("힌트1", "힌트2"), "기준 답변");

        submit(chatId, body(THOUGHT, count - 1)).andExpect(status().isAccepted());
        assertThat(thoughtOf(chatId).getSelectedChoice()).isEqualTo(count - 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void 생성되지_않은_칩_번호는_400이고_분석_작업을_남기지_않는다(int count) throws Exception {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2").subList(0, count),
                List.of("힌트1", "힌트2"), "기준 답변");

        submit(chatId, body(THOUGHT, count))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        assertThat(analysisJobRepository.findByChatId(chatId)).isEmpty();
        assertThat(thoughtRepository.findByChat(chatRepository.findById(chatId).orElseThrow())).isEmpty();
    }

    @Test
    void 칩을_고르지_않아도_제출할_수_있다() throws Exception {
        Long chatId = doneChat();

        submit(chatId, body(THOUGHT, null)).andExpect(status().isAccepted());

        assertThat(thoughtOf(chatId).getSelectedChoice()).isNull();
    }

    @Test
    void 힌트를_본_뒤_제출하면_hintUsed는_true() throws Exception {
        Long chatId = doneChat();
        jdbcTemplate.update("update chat set hint_level = 1 where id = ?", chatId);

        submit(chatId, body(THOUGHT, null))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.hintUsed").value(true));
    }

    @Test
    void 두_번_제출하면_200으로_기존_작업을_돌려주고_처음_쓴_생각은_바뀌지_않는다() throws Exception {
        Long chatId = doneChat();
        String first = submit(chatId, body(THOUGHT, 0))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        events.clear();

        String second = submit(chatId, body(THOUGHT + " 그리고 하나 더 생각해 보면요.", 1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(second).isEqualTo(first);
        assertThat(thoughtOf(chatId).getContent()).isEqualTo(THOUGHT);
        assertThat(thoughtOf(chatId).getSelectedChoice()).isEqualTo(0);
        assertThat(events.stream(AnalysisRequested.class)).isEmpty();
    }

    // ===== 검증 =====

    @Test
    void 서른_자_미만이면_400_THOUGHT_TOO_SHORT이고_작업을_만들지_않는다() throws Exception {
        Long chatId = doneChat();

        submit(chatId, body("users가 undefined예요", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THOUGHT_TOO_SHORT"));

        assertThat(analysisJobRepository.findByChatId(chatId)).isEmpty();
    }

    @Test
    void 같은_글자만_반복하면_400_THOUGHT_INVALID() throws Exception {
        submit(doneChat(), body("ㅋ".repeat(40), null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("THOUGHT_INVALID"));
    }

    @Test
    void 이천_자를_넘으면_400_INVALID_INPUT() throws Exception {
        String tooLong = (THOUGHT + " ").repeat(60).substring(0, 2001);

        submit(doneChat(), body(tooLong, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 생각이_없으면_400_INVALID_INPUT() throws Exception {
        submit(doneChat(), body(null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    @Test
    void 칩_번호가_0에서_2가_아니면_400_INVALID_INPUT() throws Exception {
        submit(doneChat(), body(THOUGHT, 3))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }

    // ===== 상태 =====

    @Test
    void 선생성이_끝나지_않았으면_409_PREPARE_NOT_DONE() throws Exception {
        Long chatId = preparingChat(session);

        submit(chatId, body(THOUGHT, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREPARE_NOT_DONE"));

        assertThat(thoughtRepository.findAll()).noneMatch(t -> t.getChat().getId().equals(chatId));
    }

    @Test
    void 기초_학습을_시작했으면_409_ALREADY_SUBMITTED() throws Exception {
        Long chatId = doneChat();
        lockService.startJob(session, chatId, AnalysisMode.BASIC);

        submit(chatId, body(THOUGHT, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    }

    @Test
    void 남의_대화는_404() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));

        submit(preparingChat(other), body(THOUGHT, null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/thoughts", doneChat())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(THOUGHT, null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
