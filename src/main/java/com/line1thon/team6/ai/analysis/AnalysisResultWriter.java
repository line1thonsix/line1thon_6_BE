package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.analysis.service.AnalysisJobService;
import com.line1thon.team6.chat.service.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 분석 결과를 작업에 저장한다. AI 호출이 끝난 뒤 짧은 트랜잭션으로만 쓴다.
 * 성공하면 결과 저장과 LockService.completeJob(DONE + 잠금 해제)을 같은 트랜잭션에서 한다 (A와 합의, PR #10).
 * PENDING이 아닌 작업(이미 끝났거나 실패로 바뀐 작업)이면 아무것도 바꾸지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultWriter {

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

    private Optional<AnalysisJob> pendingJob(Long chatId, Long jobId, String what) {
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
