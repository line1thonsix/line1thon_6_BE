package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.ai.analysis.AnalysisResultWriter;
import com.line1thon.team6.ai.analysis.CompareFinding;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.entity.Thought;
import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.repository.ThoughtRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/chats/{chatId}/analysis, POST /api/chats/{chatId}/analysis/retry.
 * 테스트 설정은 분석 리스너가 꺼져 있어서, 작업 결과는 AnalysisResultWriter로 직접 만든다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class AnalysisApiTest {

    private static final String THOUGHT = "첫 렌더링 때 users 값부터 볼게요. 데이터가 오기 전에는 undefined라서요.";
    private static final List<CompareFinding> FINDINGS = List.of(
            new CompareFinding("COMMON", "초기값 확인", "첫 렌더링 때 users 값부터 볼게요.", "처음에는 users가 undefined예요.",
                    "같은 지점을 봤어요.", null),
            new CompareFinding("UNDETERMINED", "조건이 더 필요해요", null, null, "근거 부족", "fetch가 성공했나요?"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    ThoughtRepository thoughtRepository;

    @Autowired
    ChatService chatService;

    @Autowired
    LockService lockService;

    @Autowired
    AnalysisResultWriter writer;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    ApplicationEvents events;

    GuestSession session;
    String token;

    @BeforeEach
    void setUp() {
        token = UUID.randomUUID().toString();
        session = guestSessionRepository.save(new GuestSession(token));
    }

    private Long doneChat(GuestSession owner) {
        Long chatId = chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "users가 undefined라고 나와요", null, false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변 전문");
        return chatId;
    }

    /** 생각을 제출한 상태 (PENDING 비교 분석 작업 + 생각) */
    private Submitted submitted() {
        Long chatId = doneChat(session);
        AnalysisJob job = lockService.startJob(session, chatId, AnalysisMode.COMPARE).job();
        thoughtRepository.save(Thought.create(chatRepository.findById(chatId).orElseThrow(), THOUGHT, 0));
        return new Submitted(chatId, job.getId());
    }

    private record Submitted(Long chatId, Long jobId) {
    }

    private Submitted failed() {
        Submitted s = submitted();
        writer.fail(s.chatId(), s.jobId(), AiErrorCode.AI_API_ERROR);
        return s;
    }

    private ResultActions getAnalysis(Long chatId) throws Exception {
        return mockMvc.perform(get("/api/chats/{chatId}/analysis", chatId).header("X-Guest-Token", token));
    }

    private ResultActions retry(Long chatId) throws Exception {
        return mockMvc.perform(post("/api/chats/{chatId}/analysis/retry", chatId).header("X-Guest-Token", token));
    }

    private AnalysisJob job(Long jobId) {
        return analysisJobRepository.findById(jobId).orElseThrow();
    }

    /**
     * 지금 시도를 시작한 시각을 과거로 옮긴다 (멈춘 작업 흉내).
     * hibernate.jdbc.time_zone 변환을 똑같이 거치도록 JDBC가 아니라 JPQL로 바꾼다 (서버 시간대가 UTC인 CI에서도 맞게)
     */
    private void startedAgo(Long jobId, Duration ago) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update AnalysisJob j set j.startedAt = :startedAt where j.id = :id")
                .setParameter("startedAt", KoreaTime.now().minus(ago))
                .setParameter("id", jobId)
                .executeUpdate());
    }

    // ===== 조회 =====

    @Test
    void 분석_중이면_PENDING과_내_생각만_주고_결과와_기준_답변은_가린다() throws Exception {
        Submitted s = submitted();

        getAnalysis(s.chatId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(s.jobId()))
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.mode").value("COMPARE"))
                .andExpect(jsonPath("$.unlockType").isEmpty())
                .andExpect(jsonPath("$.myThought").value(THOUGHT))
                .andExpect(jsonPath("$.referenceAnswer").isEmpty())
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.errorCode").isEmpty())
                .andExpect(jsonPath("$.isDemo").value(false))
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.remainingRetries").value(2));
    }

    @Test
    void 분석이_끝나면_기준_답변과_순서대로_id를_붙인_findings를_준다() throws Exception {
        Submitted s = submitted();
        writer.completeCompare(s.chatId(), s.jobId(), FINDINGS);

        getAnalysis(s.chatId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("DONE"))
                .andExpect(jsonPath("$.unlockType").value("SELF"))
                .andExpect(jsonPath("$.referenceAnswer").value("기준 답변 전문"))
                .andExpect(jsonPath("$.notice").isNotEmpty())
                .andExpect(jsonPath("$.findings.length()").value(2))
                .andExpect(jsonPath("$.findings[0].id").value(0))
                .andExpect(jsonPath("$.findings[0].category").value("COMMON"))
                .andExpect(jsonPath("$.findings[0].title").value("초기값 확인"))
                .andExpect(jsonPath("$.findings[0].userQuote").value("첫 렌더링 때 users 값부터 볼게요."))
                .andExpect(jsonPath("$.findings[0].aiPerspective").value("처음에는 users가 undefined예요."))
                .andExpect(jsonPath("$.findings[0].detail").value("같은 지점을 봤어요."))
                .andExpect(jsonPath("$.findings[1].id").value(1))
                .andExpect(jsonPath("$.findings[1].userQuote").isEmpty())
                .andExpect(jsonPath("$.findings[1].followUpQuestion").value("fetch가 성공했나요?"))
                .andExpect(jsonPath("$.basicExplanation").isEmpty());
    }

    @Test
    void 실패하면_오류_코드와_남은_재시도를_준다() throws Exception {
        Submitted s = failed();

        getAnalysis(s.chatId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI_API_ERROR"))
                .andExpect(jsonPath("$.findings").isEmpty())
                .andExpect(jsonPath("$.referenceAnswer").isEmpty())
                .andExpect(jsonPath("$.remainingRetries").value(2));
    }

    @Test
    void 이분_넘게_PENDING이면_조회할_때_AI_TIMEOUT으로_실패_처리한다() throws Exception {
        Submitted s = submitted();
        startedAgo(s.jobId(), Duration.ofMinutes(3));

        getAnalysis(s.chatId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("AI_TIMEOUT"));
        assertThat(job(s.jobId()).getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void 이분이_안_됐으면_PENDING_그대로다() throws Exception {
        Submitted s = submitted();
        startedAgo(s.jobId(), Duration.ofSeconds(90));

        getAnalysis(s.chatId()).andExpect(jsonPath("$.analysisStatus").value("PENDING"));
    }

    @Test
    void 생각을_제출하지_않은_대화면_404_ANALYSIS_NOT_FOUND() throws Exception {
        getAnalysis(doneChat(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @Test
    void 남의_대화면_404_CHAT_NOT_FOUND이고_토큰이_없으면_401() throws Exception {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChat = doneChat(other);
        lockService.startJob(other, othersChat, AnalysisMode.COMPARE);

        getAnalysis(othersChat)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_NOT_FOUND"));
        mockMvc.perform(get("/api/chats/{chatId}/analysis", othersChat))
                .andExpect(status().isUnauthorized());
    }

    // ===== 재시도 =====

    @Test
    void 실패한_작업을_재시도하면_202와_PENDING이고_분석을_다시_요청한다() throws Exception {
        Submitted s = failed();

        retry(s.chatId())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(s.jobId()))
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.retryCount").value(1))
                .andExpect(jsonPath("$.remainingRetries").value(1));

        AnalysisJob job = job(s.jobId());
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(job.getAttemptCount()).isEqualTo(2);
        assertThat(job.getErrorCode()).isNull();
        assertThat(events.stream(AnalysisRequested.class))
                .contains(new AnalysisRequested(s.chatId(), s.jobId()));
        getAnalysis(s.chatId())
                .andExpect(jsonPath("$.analysisStatus").value("PENDING"))
                .andExpect(jsonPath("$.retryCount").value(1));
    }

    @Test
    void 재시도하면_멈춘_작업_기준_시간도_다시_시작한다() throws Exception {
        Submitted s = failed();
        startedAgo(s.jobId(), Duration.ofMinutes(10));

        retry(s.chatId()).andExpect(status().isAccepted());

        getAnalysis(s.chatId()).andExpect(jsonPath("$.analysisStatus").value("PENDING"));
    }

    @Test
    void 분석_중이거나_끝난_작업은_409_NOT_RETRYABLE() throws Exception {
        Submitted pending = submitted();
        retry(pending.chatId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_RETRYABLE"));

        Submitted done = submitted();
        writer.completeCompare(done.chatId(), done.jobId(), FINDINGS);
        retry(done.chatId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOT_RETRYABLE"));
    }

    @Test
    void 세_번_모두_실패하면_409_RETRY_LIMIT_EXCEEDED() throws Exception {
        Submitted s = failed();
        retry(s.chatId()).andExpect(status().isAccepted());
        writer.fail(s.chatId(), s.jobId(), AiErrorCode.AI_TIMEOUT);
        retry(s.chatId()).andExpect(status().isAccepted());
        writer.fail(s.chatId(), s.jobId(), AiErrorCode.AI_TIMEOUT);

        getAnalysis(s.chatId()).andExpect(jsonPath("$.remainingRetries").value(0));
        retry(s.chatId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RETRY_LIMIT_EXCEEDED"));
        assertThat(job(s.jobId()).getAttemptCount()).isEqualTo(3);
    }

    @Test
    void 분석_작업이_없으면_404_ANALYSIS_NOT_FOUND() throws Exception {
        retry(doneChat(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_NOT_FOUND"));
    }

    @Test
    void 재시도를_동시에_두_번_눌러도_한_번만_재시도한다() throws Exception {
        Submitted s = failed();
        int requests = 2;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<String> call = () -> {
                    ready.countDown();
                    start.await();
                    try {
                        lockService.retryJob(session, s.chatId());
                        return "OK";
                    } catch (BusinessException e) {
                        return e.getErrorCode().getCode();
                    }
                };
                futures.add(pool.submit(call));
            }
            ready.await();
            start.countDown();

            List<String> results = new ArrayList<>();
            for (Future<String> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).containsExactlyInAnyOrder("OK", "NOT_RETRYABLE");
            assertThat(job(s.jobId()).getAttemptCount()).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }
}
