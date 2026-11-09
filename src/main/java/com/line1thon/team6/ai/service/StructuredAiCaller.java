package com.line1thon.team6.ai.service;

import com.line1thon.team6.ai.client.LlmClient;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.client.LlmResponse;
import com.line1thon.team6.ai.config.AiProperties;
import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * JSON 출력 AI 호출과 형식 오류 재호출 (AI 담당 전달 명세 R5).
 * <ul>
 *   <li>형식이 틀리면 이전 응답과 수정 지시를 붙여 다시 부른다. 첫 호출 포함 최대 maxAttempts번, 끝내 틀리면 AI_INVALID_JSON</li>
 *   <li>시간 초과·제공사 오류는 다시 부르지 않는다. 사용량 초과(429)는 곧바로 다시 불러도 또 막히고,
 *       시간 초과를 반복하면 선생성이 1분 기준을 넘는다. 재시도는 사용자의 재시도 요청으로 한다</li>
 *   <li>호출마다 ai_usage_log에 남긴다</li>
 * </ul>
 */
@Slf4j
@Component
public class StructuredAiCaller {

    private final LlmClient llmClient;
    private final AiUsageRecorder usageRecorder;
    private final int maxAttempts;

    public StructuredAiCaller(LlmClient llmClient, AiUsageRecorder usageRecorder, AiProperties properties) {
        this.llmClient = llmClient;
        this.usageRecorder = usageRecorder;
        this.maxAttempts = Math.max(1, properties.maxAttempts());
    }

    public <T> T call(AiCallContext context, LlmRequest request, AiOutputParser<T> parser) {
        LlmRequest current = request;
        for (int attempt = 1; ; attempt++) {
            long start = System.nanoTime();
            LlmResponse response;
            try {
                response = llmClient.generate(current);
            } catch (AiException e) {
                usageRecorder.failure(context, llmClient.model(), null, elapsedMs(start), e.getErrorCode());
                throw e;
            }
            long latencyMs = elapsedMs(start);
            try {
                T result = parser.parse(response.text());
                usageRecorder.success(context, llmClient.model(), response, latencyMs);
                return result;
            } catch (AiOutputInvalidException e) {
                usageRecorder.failure(context, llmClient.model(), response, latencyMs, AiErrorCode.AI_INVALID_JSON);
                // 응답 원문은 사용자 내용이 섞여 있어 로그에 남기지 않는다
                log.warn("AI 출력 형식 오류: callType={} attempt={}/{} finishReason={} problems={}",
                        context.callType(), attempt, maxAttempts, response.finishReason(), e.getProblems());
                if (attempt >= maxAttempts) {
                    throw new AiException(AiErrorCode.AI_INVALID_JSON,
                            maxAttempts + "번 호출했지만 출력 형식이 맞지 않음", e);
                }
                current = current.followUp(response.text(), CorrectionPrompt.of(e.getProblems()));
            }
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
