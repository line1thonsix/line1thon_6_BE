package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.chat.event.PrepareRequested;
import com.line1thon.team6.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 질문 생성·선생성 재시도가 커밋된 뒤 백그라운드에서 선생성을 실행한다.
 * 커밋 전에 실행하면 아직 저장되지 않은 채팅을 읽거나, 롤백된 요청으로 AI를 부르게 된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "deveil.ai.listener-enabled", havingValue = "true", matchIfMissing = true)
public class PrepareListener {

    private final PrepareService prepareService;

    @Async(AsyncConfig.AI_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPrepareRequested(PrepareRequested event) {
        prepareService.prepare(event.chatId());
    }
}
