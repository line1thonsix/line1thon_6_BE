package com.line1thon.team6.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 호출 설정 (application.properties의 deveil.ai.*).
 * 값은 AI 담당 전달 명세 R4를 따른다.
 *
 * @param apiKey         GEMINI_API_KEY. 비어 있으면 가짜 클라이언트를 쓴다 (운영 프로필은 비어 있으면 실행 실패)
 * @param model          DEVEIL_MODEL
 * @param baseUrl        Gemini API 주소
 * @param timeout        호출 한 번의 제한 시간 (15초)
 * @param thinkingLevel  Gemini 3 추론 수준. 기본 MINIMAL (비용·응답 시간 우선)
 * @param maxAttempts    형식 오류 시 첫 호출 포함 최대 호출 수 (R5)
 */
@ConfigurationProperties(prefix = "deveil.ai")
public record AiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout,
        ThinkingLevel thinkingLevel,
        int maxAttempts
) {

    public enum ThinkingLevel {
        MINIMAL, LOW, MEDIUM, HIGH
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
