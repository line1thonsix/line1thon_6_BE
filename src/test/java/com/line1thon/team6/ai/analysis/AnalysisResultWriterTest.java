package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.repository.AnalysisJobRepository;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.chat.service.LockService;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.repository.GuestSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 분석 결과 저장은 채팅 행을 잠근 뒤 작업 상태를 확인한다 (PR #14 리뷰) */
@SpringBootTest
class AnalysisResultWriterTest {

    private static final List<CompareFinding> FINDINGS =
            List.of(new CompareFinding("COMMON", "제목", null, "관점", "근거", null));

    @Autowired
    AnalysisResultWriter writer;

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
    PlatformTransactionManager transactionManager;

    Long chatId;
    Long jobId;

    @BeforeEach
    void setUp() {
        GuestSession session = guestSessionRepository.save(new GuestSession(UUID.randomUUID().toString()));
        chatId = chatRepository.save(Chat.create(session, UUID.randomUUID().toString(), QuestionType.CONCEPT,
                null, "질문", null, false)).getId();
        chatService.completePrepare(chatId, List.of("칩1", "칩2", "칩3"), List.of("힌트1", "힌트2"), "기준 답변");
        jobId = lockService.startJob(session, chatId, AnalysisMode.COMPARE).job().getId();
    }

    private AnalysisJob job() {
        return analysisJobRepository.findById(jobId).orElseThrow();
    }

    private ChatStatus chatStatus() {
        return chatRepository.findById(chatId).orElseThrow().getStatus();
    }

    @Test
    void 다른_트랜잭션이_행을_잠그고_실패로_바꾸는_중이면_기다렸다가_늦은_결과를_버린다() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        // 멈춘 작업 시간 초과 처리를 흉내 낸다: 행을 잠그고 바꾼 뒤 잠깐 붙잡고 있다가 커밋
        CompletableFuture<Void> timeout = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    chatRepository.findByIdForUpdate(chatId).orElseThrow();
                    analysisJobRepository.findById(jobId).orElseThrow().fail(AiErrorCode.AI_TIMEOUT.name());
                    locked.countDown();
                    sleep(500);
                }));
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        boolean saved = writer.completeCompare(chatId, jobId, FINDINGS);

        timeout.get(5, TimeUnit.SECONDS);
        assertThat(saved).isFalse();
        assertThat(job().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(job().getErrorCode()).isEqualTo("AI_TIMEOUT");
        assertThat(job().getFindings()).isNull();
        assertThat(chatStatus()).isEqualTo(ChatStatus.LOCKED);
    }

    @Test
    void PENDING이면_결과를_저장하고_잠금을_푼다() {
        assertThat(writer.completeCompare(chatId, jobId, FINDINGS)).isTrue();

        assertThat(job().getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job().getFindings()).hasSize(1);
        assertThat(chatStatus()).isEqualTo(ChatStatus.UNLOCKED_SELF);
    }

    @Test
    void 이미_끝난_작업은_실패로_바꾸지_않는다() {
        writer.completeCompare(chatId, jobId, FINDINGS);

        assertThat(writer.fail(chatId, jobId, AiErrorCode.AI_TIMEOUT)).isFalse();

        assertThat(job().getStatus()).isEqualTo(AnalysisStatus.DONE);
        assertThat(job().getErrorCode()).isNull();
    }

    @Test
    void 없는_채팅이나_다른_작업이면_아무것도_바꾸지_않는다() {
        assertThat(writer.completeCompare(Long.MAX_VALUE, jobId, FINDINGS)).isFalse();
        assertThat(writer.fail(chatId, Long.MAX_VALUE, AiErrorCode.AI_TIMEOUT)).isFalse();

        assertThat(job().getStatus()).isEqualTo(AnalysisStatus.PENDING);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
