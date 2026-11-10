package com.line1thon.team6.ai.exception;

import lombok.Getter;

/** AI 호출 실패. 백그라운드 작업에서만 던지고, API 응답으로는 나가지 않는다 */
@Getter
public class AiException extends RuntimeException {

    private final AiErrorCode errorCode;

    public AiException(AiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiException(AiErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
