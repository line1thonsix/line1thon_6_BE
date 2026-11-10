package com.line1thon.team6.analysis.dto;

import com.line1thon.team6.analysis.entity.AnalysisJob;
import com.line1thon.team6.analysis.entity.AnalysisStatus;

/** POST /api/chats/{chatId}/analysis/retry 응답 (202). 결과는 GET /analysis 폴링 */
public record AnalysisRetryResponse(Long jobId, AnalysisStatus analysisStatus, int retryCount, int remainingRetries) {

    public static AnalysisRetryResponse from(AnalysisJob job) {
        return new AnalysisRetryResponse(job.getId(), job.getStatus(), job.retryCount(), job.remainingRetries());
    }
}
