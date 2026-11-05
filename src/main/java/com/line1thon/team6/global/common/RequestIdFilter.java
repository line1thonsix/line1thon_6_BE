package com.line1thon.team6.global.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청마다 requestId를 만들어 로그(MDC)와 응답 헤더에 넣는다.
 * 오류 응답의 requestId와 서버 로그를 이 값으로 맞춰 볼 수 있다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = generate();
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 현재 요청의 requestId. 필터를 거치지 않은 경우에는 새로 만든다. */
    public static String current() {
        String requestId = MDC.get(MDC_KEY);
        return requestId != null ? requestId : generate();
    }

    private static String generate() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
