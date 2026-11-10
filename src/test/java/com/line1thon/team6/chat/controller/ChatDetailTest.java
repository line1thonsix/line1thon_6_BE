package com.line1thon.team6.chat.controller;

import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    LockService lockService;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

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

    // ===== 생각 제출, 분석 작업 =====

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";

    private void submitThought(Long chatId, Integer selectedChoice) throws Exception {
        mockMvc.perform(post("/api/chats/{chatId}/thoughts", chatId)
                        .header("X-Guest-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content": "%s", "selectedChoice": %s}
                                """.formatted(THOUGHT, selectedChoice)))
                .andExpect(status().isAccepted());
    }

    @Test
    void 생각을_제출하면_제출한_생각과_PENDING_분석_작업을_돌려준다() throws Exception {
        Long chatId = doneChat();
        submitThought(chatId, 1);
        Long jobId = analysisJobRepository.findByChatId(chatId).orElseThrow().getId();

        getDetail(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOCKED"))
                .andExpect(jsonPath("$.thought").value(THOUGHT))
                .andExpect(jsonPath("$.selectedChoice").value(1))
                .andExpect(jsonPath("$.hintUsed").value(false))
                .andExpect(jsonPath("$.analysis.jobId").value(jobId))
                .andExpect(jsonPath("$.analysis.mode").value("COMPARE"))
                .andExpect(jsonPath("$.analysis.status").value("PENDING"))
                .andExpect(jsonPath("$.analysis.retryCount").value(0))
                .andExpect(jsonPath("$.analysis.remainingRetries").value(2));
    }

    @Test
    void 힌트를_보고_칩_없이_제출하면_hintUsed가_true이고_칩_번호는_null() throws Exception {
        Long chatId = doneChat();
        setHintLevel(chatId, 1);
        submitThought(chatId, null);

        getDetail(chatId)
                .andExpect(jsonPath("$.thought").value(THOUGHT))
                .andExpect(jsonPath("$.selectedChoice").isEmpty())
                .andExpect(jsonPath("$.hintUsed").value(true));
    }

    @Test
    void 힌트_2단계여도_분석_작업이_있으면_기초_학습을_못_한다() throws Exception {
        Long chatId = doneChat();
        setHintLevel(chatId, 2);
        submitThought(chatId, null);

        getDetail(chatId)
                .andExpect(jsonPath("$.hasNextHint").value(false))
                .andExpect(jsonPath("$.basicModeAvailable").value(false));
    }

    @Test
    void 기초_학습으로_만든_작업은_BASIC으로_돌려주고_생각은_null() throws Exception {
        Long chatId = doneChat();
        setHintLevel(chatId, 2);
        lockService.startJob(session, chatId, AnalysisMode.BASIC);

        getDetail(chatId)
                .andExpect(jsonPath("$.thought").isEmpty())
                .andExpect(jsonPath("$.hintUsed").value(false))
                .andExpect(jsonPath("$.basicModeAvailable").value(false))
                .andExpect(jsonPath("$.analysis.mode").value("BASIC"))
                .andExpect(jsonPath("$.analysis.status").value("PENDING"));
    }

    @Test
    void 분석이_끝나도_분석_작업을_DONE으로_돌려준다() throws Exception {
        Long chatId = doneChat();
        submitThought(chatId, 0);
        Long jobId = analysisJobRepository.findByChatId(chatId).orElseThrow().getId();
        lockService.completeJob(chatId, jobId);

        getDetail(chatId)
                .andExpect(jsonPath("$.status").value("UNLOCKED_SELF"))
                .andExpect(jsonPath("$.thought").value(THOUGHT))
                .andExpect(jsonPath("$.analysis.jobId").value(jobId))
                .andExpect(jsonPath("$.analysis.status").value("DONE"));
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

    // ===== 1분 시간 초과 (멈춘 선생성 복구) =====

    /**
     * 선생성 시작 시각을 과거로 돌린다.
     * 시각은 앱과 똑같이 Hibernate로 넣는다. JdbcTemplate로 넣으면 hibernate.jdbc.time_zone 변환을 거치지 않아서
     * 서버 시간대가 한국이 아닐 때(CI는 UTC) 9시간 어긋난다.
     */
    private void startedSecondsAgo(Long chatId, long seconds) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update Chat c set c.prepareStartedAt = :startedAt where c.id = :id")
                .setParameter("startedAt", KoreaTime.now().minusSeconds(seconds))
                .setParameter("id", chatId)
                .executeUpdate());
    }

    @Test
    void 선생성_시작_후_1분이_안_됐으면_PREPARING_그대로() throws Exception {
        Long chatId = newChat(session);
        startedSecondsAgo(chatId, 50);

        getDetail(chatId).andExpect(jsonPath("$.prepareStatus").value("PREPARING"));

        assertThat(chatRepository.findById(chatId).orElseThrow().getPrepareStatus())
                .isEqualTo(PrepareStatus.PREPARING);
    }

    @Test
    void 선생성_시작_후_1분이_넘었으면_FAILED로_바꿔서_돌려준다() throws Exception {
        Long chatId = newChat(session);
        startedSecondsAgo(chatId, 61);

        getDetail(chatId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepareStatus").value("FAILED"))
                .andExpect(jsonPath("$.choices").isEmpty());

        assertThat(chatRepository.findById(chatId).orElseThrow().getPrepareStatus())
                .isEqualTo(PrepareStatus.FAILED);
    }

    @Test
    void 시간_초과로_FAILED가_된_뒤_늦게_온_선생성_결과는_버린다() throws Exception {
        Long chatId = newChat(session);
        startedSecondsAgo(chatId, 61);
        getDetail(chatId).andExpect(jsonPath("$.prepareStatus").value("FAILED"));

        chatService.completePrepare(chatId, CHOICES, HINTS, REFERENCE_ANSWER);

        Chat chat = chatRepository.findById(chatId).orElseThrow();
        assertThat(chat.getPrepareStatus()).isEqualTo(PrepareStatus.FAILED);
        assertThat(chat.getChoices()).isNull();
    }

    @Test
    void 이미_DONE이면_1분이_지나도_바꾸지_않는다() throws Exception {
        Long chatId = doneChat();
        startedSecondsAgo(chatId, 600);

        getDetail(chatId)
                .andExpect(jsonPath("$.prepareStatus").value("DONE"))
                .andExpect(jsonPath("$.choices", hasSize(3)));
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
