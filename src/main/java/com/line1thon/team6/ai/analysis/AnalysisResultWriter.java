package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.service.AnalysisJobService;
import com.line1thon.team6.chat.repository.ChatRepository;
import com.line1thon.team6.chat.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 분석 결과를 작업에 저장한다. AI 호출이 끝난 뒤 짧은 트랜잭션으로만 쓴다.
 * 성공하면 결과 저장과 LockService.completeJob(DONE + 잠금 해제)을 같은 트랜잭션에서 한다 (A와 합의, PR #10).
 * <p>
 * 작업 상태를 바꾸는 일은 모두 채팅 행 잠금(SELECT ... FOR UPDATE) 아래에서 한다 (PR #14 리뷰).
 * 그래서 상태 확인도 행을 잠근 뒤에 한다. 시간 초과·재시도처럼 같은 작업을 바꾸는 처리와 겹쳐도 차례로 실행되고,
 * 먼저 PENDING이 아니게 바뀌었으면 늦게 온 결과는 버려진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultWriter {

    private final ChatRepository chatRepository;
    private final AnalysisJobService analysisJobService;
    private final LockService lockService;

    /** @return 저장했으면 true, PENDING이 아니어서 버렸으면 false */
    @Transactional
    public boolean completeCompare(Long chatId, Long jobId, List<CompareFinding> findings) {
        Optional<AnalysisJob> job = pendingJob(chatId, jobId, "결과");
        if (job.isEmpty()) {
            return false;
        }
        job.get().recordFindings(findings.stream().map(CompareFinding::toMap).toList());
        lockService.completeJob(chatId, jobId);
        return true;
    }

    /** @return 실패로 바꿨으면 true, PENDING이 아니어서 무시했으면 false */
    @Transactional
    public boolean fail(Long chatId, Long jobId, AiErrorCode errorCode) {
        Optional<AnalysisJob> job = pendingJob(chatId, jobId, "실패");
        job.ifPresent(j -> j.fail(errorCode.name()));
        return job.isPresent();
    }

    /**
     * 오래 PENDING인 작업을 AI_TIMEOUT으로 실패 처리한다 (서버 재시작 등으로 백그라운드 작업이 사라진 경우).
     * 분석 조회 때 부른다. 행을 잠근 뒤 다시 확인하므로, 그 사이 결과가 저장됐으면 바꾸지 않는다.
     *
     * @return 실패로 바꿨으면 true
     */
    @Transactional
    public boolean failIfStale(Long chatId, Long jobId, LocalDateTime threshold) {
        Optional<AnalysisJob> job = pendingJob(chatId, jobId, "시간 초과 처리");
        if (job.isEmpty() || !job.get().isStale(threshold)) {
            return false;
        }
        job.get().fail(AiErrorCode.AI_TIMEOUT.name());
        log.warn("오래 멈춘 분석 작업을 실패로 바꿈: chatId={} jobId={} attemptCount={}",
                chatId, jobId, job.get().getAttemptCount());
        return true;
    }

    /** 채팅 행을 잠근 뒤 PENDING인 작업을 읽는다. 채팅이 없거나 PENDING이 아니면 empty */
    private Optional<AnalysisJob> pendingJob(Long chatId, Long jobId, String what) {
        if (chatRepository.findByIdForUpdate(chatId).isEmpty()) {
            // 결과가 오기 전에 사용자가 대화를 지운 경우
            log.warn("분석 {}를 버림: 채팅 없음 chatId={} jobId={}", what, chatId, jobId);
            return Optional.empty();
        }
        Optional<AnalysisJob> job = analysisJobService.findByChatId(chatId).filter(j -> j.getId().equals(jobId));
        if (job.isEmpty()) {
            log.warn("분석 {}를 버림: 작업 없음 chatId={} jobId={}", what, chatId, jobId);
            return Optional.empty();
        }
        if (job.get().getStatus() != AnalysisStatus.PENDING) {
            log.warn("분석 {}를 버림: PENDING이 아님 chatId={} jobId={} status={}",
                    what, chatId, jobId, job.get().getStatus());
            return Optional.empty();
        }
        return job;
    }
}
