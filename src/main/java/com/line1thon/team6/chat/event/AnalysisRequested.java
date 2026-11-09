package com.line1thon.team6.chat.event;

/**
 * 분석 작업을 PENDING으로 만들었다는 이벤트. LockService.startJob이 작업을 새로 만들 때 같은 트랜잭션에서 발행한다.
 * B의 리스너가 커밋 후(AFTER_COMMIT) 받아서 비교 분석(COMPARE) 또는 기초 설명(BASIC)을 실행한다.
 */
public record AnalysisRequested(Long chatId, Long jobId) {
}
