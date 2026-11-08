package com.line1thon.team6.chat.exception;

import com.line1thon.team6.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 채팅 기능의 오류 (docs/error-codes.md의 API별 오류) */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    PREPARE_NOT_FAILED(HttpStatus.CONFLICT, "PREPARE_NOT_FAILED", "이미 준비가 끝났거나 준비 중인 질문이에요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
