package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.exception.AiException;

/** LLM 제공사 호출. 형식 검증과 재호출은 호출하는 쪽(StructuredAiCaller)이 한다 */
public interface LlmClient {

    /**
     * 한 번 호출한다.
     *
     * @throws AiException 시간 초과(AI_TIMEOUT) 또는 제공사 오류(AI_API_ERROR)
     */
    LlmResponse generate(LlmRequest request);

    /** ai_usage_log.model에 남길 모델 이름 */
    String model();
}
