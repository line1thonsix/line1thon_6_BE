package com.line1thon.team6.chat.service;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 잠금 상태 머신: 분석 작업 시작(T4, T5)과 완료(T6) */
@SpringBootTest
@RecordApplicationEvents
class LockServiceTest {

    @Autowired
    LockService lockService;

    @Autowired
    ChatService chatService;

    @Autowired
    GuestSessionRepository guestSessionRepository;

    @Autowired
    ChatRepository chatRepository;

    @Autowired
    AnalysisJobRepository analysisJobRepository;

    @Autowired
    ApplicationEvents events;

    GuestSession session;

    @BeforeEach
    void setUp() {
        session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
    }

    private Long preparingChat(GuestSession owner) {
        return chatRepository.save(Chat.create(owner, UUID.randomUUID().toString(), QuestionType.DEBUGGING,
                null, "질문", null, false)).getId();
    }

    private Long doneChat() {
        Long chatId = preparingChat(session);
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        return chatId;
    }

    private static void assertError(Runnable call, Object errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    // ===== startJob =====

    @Test
    void 작업을_PENDING으로_만들고_이벤트를_발행한다() {
        Long chatId = doneChat();

        LockService.StartResult result = lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        assertThat(result.created()).isTrue();
        AnalysisJob job = analysisJobRepository.findByChatId(chatId).orElseThrow();
        assertThat(job.getId()).isEqualTo(result.job().getId());
        assertThat(job.getMode()).isEqualTo(AnalysisMode.COMPARE);
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.retryCount()).isZero();
        assertThat(job.remainingRetries()).isEqualTo(2);
        assertThat(events.stream(AnalysisRequested.class))
                .containsExactly(new AnalysisRequested(chatId, job.getId()));
    }

    @Test
    void 작업을_만들어도_채팅은_LOCKED_그대로() {
        Long chatId = doneChat();

        lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.LOCKED);
    }

    @Test
    void 같은_길로_다시_요청하면_기존_작업을_돌려주고_이벤트는_다시_발행하지_않는다() {
        Long chatId = doneChat();
        Long firstJobId = lockService.startJob(session, chatId, AnalysisMode.COMPARE).job().getId();
        events.clear();

        LockService.StartResult second = lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        assertThat(second.created()).isFalse();
        assertThat(second.job().getId()).isEqualTo(firstJobId);
        assertThat(analysisJobRepository.findByChatId(chatId)).get().extracting(AnalysisJob::getId).isEqualTo(firstJobId);
        assertThat(events.stream(AnalysisRequested.class)).isEmpty();
    }

    @Test
    void 기초_학습을_시작했으면_생각_제출은_ALREADY_SUBMITTED() {
        Long chatId = doneChat();
        lockService.startJob(session, chatId, AnalysisMode.BASIC);

        assertError(() -> lockService.startJob(session, chatId, AnalysisMode.COMPARE), ChatErrorCode.ALREADY_SUBMITTED);
    }

    @Test
    void 생각을_제출했으면_기초_학습은_ALREADY_SUBMITTED() {
        Long chatId = doneChat();
        lockService.startJob(session, chatId, AnalysisMode.COMPARE);

        assertError(() -> lockService.startJob(session, chatId, AnalysisMode.BASIC), ChatErrorCode.ALREADY_SUBMITTED);
    }

    @Test
    void 선생성이_끝나지_않았으면_PREPARE_NOT_DONE() {
        Long chatId = preparingChat(session);

        assertError(() -> lockService.startJob(session, chatId, AnalysisMode.COMPARE), ChatErrorCode.PREPARE_NOT_DONE);
        assertThat(analysisJobRepository.findByChatId(chatId)).isEmpty();
    }

    @Test
    void 남의_대화는_CHAT_NOT_FOUND() {
        GuestSession other = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        Long othersChatId = preparingChat(other);

        assertError(() -> lockService.startJob(session, othersChatId, AnalysisMode.COMPARE),
                GlobalErrorCode.CHAT_NOT_FOUND);
    }

    @Test
    void 동시에_두_번_요청해도_작업은_하나만_생긴다() throws Exception {
        Long chatId = doneChat();
        int requests = 2;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        try {
            List<Future<LockService.StartResult>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                Callable<LockService.StartResult> call = () -> {
                    ready.countDown();
                    start.await();
                    return lockService.startJob(session, chatId, AnalysisMode.COMPARE);
                };
                futures.add(pool.submit(call));
            }
            ready.await();
            start.countDown();

            List<LockService.StartResult> results = new ArrayList<>();
            for (Future<LockService.StartResult> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(LockService.StartResult::created).hasSize(1);
            assertThat(results).extracting(r -> r.job().getId()).containsOnly(results.get(0).job().getId());
        } finally {
            pool.shutdownNow();
        }
    }

    // ===== completeJob =====

    @Test
    void 비교_분석이_끝나면_UNLOCKED_SELF() {
        Long chatId = doneChat();
        Long jobId = lockService.startJob(session, chatId, AnalysisMode.COMPARE).job().getId();

        ChatStatus status = lockService.completeJob(chatId, jobId);

        assertThat(status).isEqualTo(ChatStatus.UNLOCKED_SELF);
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.UNLOCKED_SELF);
        AnalysisJob job = analysisJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void 기초_설명이_끝나면_UNLOCKED_ASSISTED() {
        Long chatId = doneChat();
        Long jobId = lockService.startJob(session, chatId, AnalysisMode.BASIC).job().getId();

        assertThat(lockService.completeJob(chatId, jobId)).isEqualTo(ChatStatus.UNLOCKED_ASSISTED);
    }

    @Test
    void 완료를_두_번_불러도_그대로다() {
        Long chatId = doneChat();
        Long jobId = lockService.startJob(session, chatId, AnalysisMode.COMPARE).job().getId();
        lockService.completeJob(chatId, jobId);

        assertThat(lockService.completeJob(chatId, jobId)).isEqualTo(ChatStatus.UNLOCKED_SELF);
    }

    @Test
    void 다른_채팅의_작업_ID면_IllegalArgumentException() {
        Long chatId = doneChat();
        Long otherChatId = doneChat();
        Long otherJobId = lockService.startJob(session, otherChatId, AnalysisMode.COMPARE).job().getId();

        assertThatThrownBy(() -> lockService.completeJob(chatId, otherJobId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(chatRepository.findById(chatId).orElseThrow().getStatus()).isEqualTo(ChatStatus.LOCKED);
    }
}
