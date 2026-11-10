package com.line1thon.team6.chat.dto;

import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.chat.entity.ChatStatus;

/**
 * POST /api/chats/{chatId}/thoughts 응답.
 * 분석이 끝나기 전이라 status는 LOCKED. 결과는 GET /api/chats/{chatId}/analysis(B)를 2초마다 조회한다.
 */
public record ThoughtSubmitResponse(
        Long jobId,
        AnalysisMode mode,
        AnalysisStatus analysisStatus,
        ChatStatus status,
        boolean hintUsed
) {
}
