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
 * @param temperature    0.2
 * @param thinkingBudget 생각 토큰 상한. 기본값(제한 없음)이면 최대 출력 토큰을 생각에 다 써서 JSON이 잘린다
 * @param maxAttempts    형식 오류 시 첫 호출 포함 최대 호출 수 (R5)
 */
@ConfigurationProperties(prefix = "deveil.ai")
public record AiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration timeout,
        double temperature,
        int thinkingBudget,
        int maxAttempts
) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
