package com.line1thon.team6.global.config;

import com.line1thon.team6.global.common.RequestIdFilter;
import com.line1thon.team6.session.auth.CurrentSessionArgumentResolver;
import com.line1thon.team6.session.auth.GuestTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final GuestTokenInterceptor guestTokenInterceptor;
    private final CurrentSessionArgumentResolver currentSessionArgumentResolver;
    private final CorsProperties corsProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guestTokenInterceptor)
                .addPathPatterns("/api/**")
                // 토큰을 발급하는 API라 토큰 없이 호출한다
                .excludePathPatterns("/api/sessions");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentSessionArgumentResolver);
    }

    /**
     * 프론트가 다른 주소에서 API를 부를 수 있게 허용한다.
     * 인증은 쿠키가 아니라 X-Guest-Token 헤더라서 allowCredentials는 켜지 않는다.
     * 오류 문의 때 쓰도록 응답의 X-Request-Id를 프론트가 읽을 수 있게 노출한다.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "DELETE")
                .allowedHeaders(GuestTokenInterceptor.HEADER, HttpHeaders.CONTENT_TYPE)
                .exposedHeaders(RequestIdFilter.HEADER)
                .maxAge(3600);
    }
}
