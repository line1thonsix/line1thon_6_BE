package com.line1thon.team6.global.config;

import com.line1thon.team6.session.auth.CurrentSessionArgumentResolver;
import com.line1thon.team6.session.auth.GuestTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final GuestTokenInterceptor guestTokenInterceptor;
    private final CurrentSessionArgumentResolver currentSessionArgumentResolver;

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
}
