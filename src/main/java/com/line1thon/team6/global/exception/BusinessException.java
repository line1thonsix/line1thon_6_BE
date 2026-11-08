package com.line1thon.team6.global.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 정해진 오류 응답을 보내야 할 때 던지는 예외.
 * 예: {@code throw new BusinessException(GlobalErrorCode.CHAT_NOT_FOUND);}
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 기본 메시지 대신 상황에 맞는 메시지를 보낼 때 사용 */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
