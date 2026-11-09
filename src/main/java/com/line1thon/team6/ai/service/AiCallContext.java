package com.line1thon.team6.ai.service;

import com.line1thon.team6.ai.entity.AiCallType;

/**
 * 호출 기록에 남길 정보.
 *
 * @param callType       PREPARE / COMPARE / BASIC
 * @param guestSessionId 사용자별 기록 조회용 (FK 아님)
 * @param promptVersion  프롬프트를 바꾸면 결과를 비교할 수 있게 버전을 남긴다
 */
public record AiCallContext(AiCallType callType, Long guestSessionId, String promptVersion) {
}
