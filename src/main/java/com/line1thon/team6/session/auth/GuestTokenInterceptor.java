package com.line1thon.team6.session.auth;

import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.service.GuestSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 요청의 X-Guest-Token 헤더로 게스트 세션을 찾아 request attribute에 넣는다.
 * 헤더가 없거나 잘못된 토큰이면 401 INVALID_TOKEN (GlobalExceptionHandler가 응답).
 */
@Component
@RequiredArgsConstructor
public class GuestTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER = "X-Guest-Token";
    static final String SESSION_ATTRIBUTE = GuestTokenInterceptor.class.getName() + ".session";

    private final GuestSessionService guestSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 컨트롤러 메서드가 아닌 요청(없는 URL, CORS preflight 등)은 검사하지 않는다 → 404 등이 그대로 나간다
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        GuestSession session = guestSessionService.getByToken(request.getHeader(HEADER));
        request.setAttribute(SESSION_ATTRIBUTE, session);
        return true;
    }
}
