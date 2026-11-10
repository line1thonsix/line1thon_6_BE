package com.line1thon.team6.ai.config;

import com.line1thon.team6.ai.client.FakeLlmClient;
import com.line1thon.team6.ai.client.GeminiClient;
import com.line1thon.team6.ai.client.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    /** 키가 있으면 Gemini, 없으면 가짜 클라이언트 (운영 프로필은 키가 없으면 설정 단계에서 실패) */
    @Bean
    public LlmClient llmClient(AiProperties properties, JsonMapper jsonMapper) {
        if (!properties.hasApiKey()) {
            log.warn("GEMINI_API_KEY가 없어 가짜 AI 클라이언트로 실행합니다. 선생성·비교 분석·기초 설명 결과는 고정 문구입니다.");
            return new FakeLlmClient(jsonMapper);
        }
        log.info("Gemini 클라이언트 사용: model={}, timeout={}", properties.model(), properties.timeout());
        return new GeminiClient(properties, jsonMapper);
    }
}
