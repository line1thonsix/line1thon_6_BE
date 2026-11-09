package com.line1thon.team6.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * CORS 설정 (application.properties의 deveil.cors.*).
 *
 * @param allowedOrigins 요청을 허용할 프론트 주소 목록. 예: http://localhost:5173
 */
@ConfigurationProperties(prefix = "deveil.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
