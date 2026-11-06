package com.line1thon.team6.session.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 컨트롤러 파라미터에 현재 요청의 게스트 세션을 넣는다.
 * 예: {@code public ChatResponse get(@CurrentSession GuestSession session) { ... }}
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentSession {
}
