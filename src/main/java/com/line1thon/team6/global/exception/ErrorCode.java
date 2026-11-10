package com.line1thon.team6.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답의 상태 코드, 오류 코드, 기본 메시지.
 * 공통 오류는 {@link GlobalErrorCode}, 기능별 오류는 각 기능 패키지에서 이 인터페이스를 구현한 enum으로 만든다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}
