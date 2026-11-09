package com.line1thon.team6.ai.service;

import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.entity.AiUsageLog;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.repository.AiUsageLogRepository;
import com.line1thon.team6.global.common.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 호출 한 번마다 ai_usage_log에 남긴다.
 * requestId는 작업을 만든 API 요청의 값이다 (AsyncConfig가 MDC를 작업 스레드로 넘긴다).
 * 기록 실패는 AI 작업 결과에 영향을 주지 않도록 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiUsageRecorder {

    private final AiUsageLogRepository repository;

    public void success(AiCallContext context, String model, LlmResponse response, long latencyMs) {
        save(context, model, response, latencyMs, null);
    }

    /** response는 응답을 받았지만 형식이 틀린 경우에만 있다 (시간 초과·제공사 오류는 null) */
    public void failure(AiCallContext context, String model, LlmResponse response, long latencyMs,
                        AiErrorCode errorCode) {
        save(context, model, response, latencyMs, errorCode);
    }

    private void save(AiCallContext context, String model, LlmResponse response, long latencyMs,
                      AiErrorCode errorCode) {
        try {
            repository.save(AiUsageLog.of(context.guestSessionId(), RequestIdFilter.current(), context.callType(),
                    model, context.promptVersion(),
                    response == null ? null : response.inputTokens(),
                    response == null ? null : response.outputTokens(),
                    (int) Math.min(latencyMs, Integer.MAX_VALUE), errorCode));
        } catch (RuntimeException e) {
            log.error("AI 사용 기록 저장 실패: callType={} errorCode={}", context.callType(), errorCode, e);
        }
    }
}
