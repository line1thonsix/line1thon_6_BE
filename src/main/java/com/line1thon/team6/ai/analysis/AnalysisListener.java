package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.chat.event.AnalysisRequested;
import com.line1thon.team6.global.config.AsyncConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 분석 작업이 만들어진 트랜잭션(생각 제출, 기초 학습)이 커밋된 뒤 백그라운드에서 분석을 실행한다.
 * 선생성 리스너와 같은 설정을 따른다 (테스트에서는 기본으로 꺼짐).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "deveil.ai.listener-enabled", havingValue = "true", matchIfMissing = true)
public class AnalysisListener {

    private final AnalysisService analysisService;

    @Async(AsyncConfig.AI_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisRequested(AnalysisRequested event) {
        analysisService.analyze(event.chatId(), event.jobId());
    }
}
