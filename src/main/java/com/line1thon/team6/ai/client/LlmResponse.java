package com.line1thon.team6.ai.client;

/**
 * LLM 응답 하나.
 *
 * @param text         모델 출력 (생각 부분 제외)
 * @param inputTokens  입력 토큰 수. 모르면 null
 * @param outputTokens 출력 토큰 수 (생각 토큰 포함, 과금 기준). 모르면 null
 * @param finishReason 종료 이유. MAX_TOKENS면 출력이 잘린 것
 */
public record LlmResponse(String text, Integer inputTokens, Integer outputTokens, String finishReason) {
}
