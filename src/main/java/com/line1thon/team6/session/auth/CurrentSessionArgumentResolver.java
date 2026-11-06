package com.line1thon.team6.session.auth;

import com.line1thon.team6.global.exception.BusinessException;
import com.line1thon.team6.global.exception.GlobalErrorCode;
import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** {@link CurrentSession}이 붙은 파라미터에 인터셉터가 찾아 둔 게스트 세션을 넣는다. */
@Component
public class CurrentSessionArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentSession.class)
                && GuestSession.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object session = webRequest.getAttribute(
                GuestTokenInterceptor.SESSION_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        // 인증 제외 경로에서 @CurrentSession을 쓴 경우
        if (session == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_TOKEN);
        }
        return session;
    }
}
