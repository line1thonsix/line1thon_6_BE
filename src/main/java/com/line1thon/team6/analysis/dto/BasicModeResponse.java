package com.line1thon.team6.analysis.dto;

import com.line1thon.team6.analysis.entity.AnalysisMode;
import com.line1thon.team6.analysis.entity.AnalysisStatus;
import com.line1thon.team6.chat.entity.ChatStatus;

/**
 * POST /api/chats/{chatId}/basic-mode 응답. 생각 제출 응답과 같은 모양이다 (hintUsed 제외).
 * 설명이 만들어지기 전이라 status는 LOCKED. 결과는 GET /analysis를 2초마다 조회한다.
 */
public record BasicModeResponse(Long jobId, AnalysisMode mode, AnalysisStatus analysisStatus, ChatStatus status) {
}
