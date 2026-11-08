package com.line1thon.team6.global.exception;

/**
 * 모든 오류 응답의 공통 형식.
 * <pre>{ "code": "THOUGHT_TOO_SHORT", "message": "...", "requestId": "req_8f2c1a9b" }</pre>
 */
public record ErrorResponse(String code, String message, String requestId) {
}
