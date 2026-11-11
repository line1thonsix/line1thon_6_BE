package com.line1thon.team6.chat.service;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.exception.AnalysisErrorCode;
import com.line1thon.team6.analysis.service.AnalysisJobService;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 잠금 상태 머신 (Notion "잠금 상태 머신"). 채팅 status와 분석 작업 생성·완료는 여기서만 바꾼다.
 * <ul>
 *   <li>startJob: 생각 제출(A, COMPARE), 기초 학습(B, BASIC) → 작업 PENDING (T4, T5)</li>
 *   <li>completeJob: B의 분석 완료 → 작업 DONE + 잠금 해제 (T6)</li>
 * </ul>
 * 두 메서드 모두 채팅 행을 잠그고(SELECT ... FOR UPDATE) 확인한 뒤 바꿔서, 동시에 요청이 와도 작업이 2개 생기지 않는다.
 * DB의 analysis_job.chat_id UNIQUE도 같은 것을 한 번 더 막는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LockService {

    private final ChatRepository chatRepository;
    private final AnalysisJobService analysisJobService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 분석 작업 시작. 같은 트랜잭션에서 AnalysisRequested를 발행하고, B의 리스너가 커밋 후 AI를 부른다.
     * <ul>
     *   <li>없거나 남의 대화: 404 CHAT_NOT_FOUND</li>
     *   <li>선생성이 DONE이 아님: 409 PREPARE_NOT_DONE</li>
     *   <li>같은 길(mode)의 작업이 이미 있음: 새로 만들지 않고 기존 작업 반환 (중복 클릭, created = false)</li>
     *   <li>다른 길의 작업이 이미 있음: 409 ALREADY_SUBMITTED</li>
     * </ul>
     * 기초 학습의 hintLevel = 2 조건은 B의 basic-mode API가 확인한다.
     */
    @Transactional
    public StartResult startJob(GuestSession session, Long chatId, AnalysisMode mode) {
        Chat chat = chatRepository.findOwnedForUpdate(chatId, session)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND));

        Optional<AnalysisJob> existing = analysisJobService.findByChatId(chat.getId());
        if (existing.isPresent()) {
            if (existing.get().getMode() != mode) {
                throw new BusinessException(ChatErrorCode.ALREADY_SUBMITTED);
            }
            return new StartResult(chat, existing.get(), false);
        }
        if (chat.getPrepareStatus() != PrepareStatus.DONE) {
            throw new BusinessException(ChatErrorCode.PREPARE_NOT_DONE);
        }

        AnalysisJob job = analysisJobService.start(chat, mode);
        eventPublisher.publishEvent(new AnalysisRequested(chat.getId(), job.getId()));
        log.info("분석 작업 시작: chatId={} jobId={} mode={}", chat.getId(), job.getId(), mode);
        return new StartResult(chat, job, true);
    }

    /**
     * 분석 완료 (T6). 작업을 DONE으로 바꾸고 고른 길에 따라 잠금을 푼다.
     * B는 결과(findings, basicExplanation)를 저장하는 트랜잭션 안에서 이 메서드를 불러야 한다.
     * 그래야 결과만 저장되고 잠금이 안 풀리는 일이 없다.
     * 이미 DONE이고 잠금이 풀렸으면 아무것도 바꾸지 않고 현재 상태를 돌려준다 (같은 완료 처리가 두 번 와도 안전).
     *
     * @return 바뀐 채팅 status (UNLOCKED_SELF / UNLOCKED_ASSISTED)
     * @throws IllegalArgumentException 이 채팅의 작업이 아님
     * @throws IllegalStateException    작업이 PENDING이 아님 (예: FAILED)
     */
    @Transactional
    public ChatStatus completeJob(Long chatId, Long jobId) {
        Chat chat = chatRepository.findByIdForUpdate(chatId)
                .orElseThrow(() -> new IllegalArgumentException("없는 채팅입니다: chatId=" + chatId));
        AnalysisJob job = analysisJobService.findByChatId(chatId)
                .filter(j -> j.getId().equals(jobId))
                .orElseThrow(() -> new IllegalArgumentException(
                        "이 채팅의 분석 작업이 아닙니다: chatId=" + chatId + " jobId=" + jobId));

        if (job.getStatus() == AnalysisStatus.DONE
                && chat.getStatus() != ChatStatus.LOCKED) {
            return chat.getStatus();
        }
        job.complete();
        chat.unlock(job.getMode());
        log.info("잠금 해제: chatId={} jobId={} status={}", chatId, jobId, chat.getStatus());
        return chat.getStatus();
    }

    /**
     * 분석 재시도 (B, PR #14 리뷰). 채팅 행을 잠근 뒤 확인하고 작업을 다시 PENDING으로 돌린다.
     * 같은 트랜잭션에서 AnalysisRequested를 발행해 B의 분석 리스너가 커밋 후 다시 AI를 부른다.
     * 채팅 status는 LOCKED 그대로다.
     * <ul>
     *   <li>없거나 남의 대화: 404 CHAT_NOT_FOUND</li>
     *   <li>분석 작업 없음: 404 ANALYSIS_NOT_FOUND</li>
     *   <li>작업이 FAILED가 아님 (PENDING, DONE): 409 NOT_RETRYABLE. 재시도를 두 번 눌러도 두 번째는 여기서 막힌다</li>
     *   <li>첫 시도 포함 3번 모두 실패: 409 RETRY_LIMIT_EXCEEDED</li>
     * </ul>
     */
    @Transactional
    public AnalysisJob retryJob(GuestSession session, Long chatId) {
        Chat chat = chatRepository.findOwnedForUpdate(chatId, session)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND));
        AnalysisJob job = analysisJobService.findByChatId(chat.getId())
                .orElseThrow(() -> new BusinessException(AnalysisErrorCode.ANALYSIS_NOT_FOUND));
        if (job.getStatus() != AnalysisStatus.FAILED) {
            throw new BusinessException(AnalysisErrorCode.NOT_RETRYABLE);
        }
        if (!job.canRetry()) {
            throw new BusinessException(AnalysisErrorCode.RETRY_LIMIT_EXCEEDED);
        }
        job.retry();
        eventPublisher.publishEvent(new AnalysisRequested(chat.getId(), job.getId()));
        log.info("분석 재시도: chatId={} jobId={} attemptCount={}", chat.getId(), job.getId(), job.getAttemptCount());
        return job;
    }

    /**
     * @param chat    행 잠금으로 읽은 채팅 (같은 트랜잭션 안에서 이어서 쓸 수 있음)
     * @param job     새로 만들었거나 이미 있던 작업
     * @param created false면 중복 요청이라 기존 작업을 돌려준 것 (API는 202 대신 200)
     */
    public record StartResult(Chat chat, AnalysisJob job, boolean created) {
    }
}
