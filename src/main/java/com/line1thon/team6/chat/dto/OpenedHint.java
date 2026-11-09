package com.line1thon.team6.chat.dto;

/**
 * ChatService.openNextHint 결과. B의 POST /api/chats/{chatId}/hints 응답에 그대로 쓸 수 있다.
 *
 * @param hintLevel          공개된 힌트 단계 1~2
 * @param hint               이번에 보여 줄 힌트 (2단계에서 다시 요청하면 마지막 힌트)
 * @param hasNextHint        더 볼 힌트가 있는지 (hintLevel < 2)
 * @param basicModeAvailable 기초 학습 버튼 표시 (hintLevel = 2. 분석 작업이 있으면 여기까지 오지 않음)
 */
public record OpenedHint(int hintLevel, String hint, boolean hasNextHint, boolean basicModeAvailable) {
}
