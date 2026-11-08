package com.line1thon.team6.chat.event;

/**
 * 선생성 요청 이벤트. 채팅을 저장한 트랜잭션 안에서 발행하고,
 * 커밋 후 B의 리스너(@TransactionalEventListener AFTER_COMMIT)가 AI를 호출한다.
 */
public record PrepareRequested(Long chatId) {
}
