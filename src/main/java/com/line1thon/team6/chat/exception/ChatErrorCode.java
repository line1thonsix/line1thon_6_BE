package com.line1thon.team6.chat.exception;

import com.line1thon.team6.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 채팅 기능의 오류 (docs/error-codes.md의 API별 오류) */
@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    PREPARE_NOT_FAILED(HttpStatus.CONFLICT, "PREPARE_NOT_FAILED", "이미 준비가 끝났거나 준비 중인 질문이에요."),
    THOUGHT_TOO_SHORT(HttpStatus.BAD_REQUEST, "THOUGHT_TOO_SHORT",
            "이유나 확인 방법을 조금 더 적어 볼까요? 30자 이상이면 함께 비교할 수 있어요."),
    THOUGHT_INVALID(HttpStatus.BAD_REQUEST, "THOUGHT_INVALID", "같은 글자만 반복하지 말고 생각을 문장으로 적어 주세요."),
    PREPARE_NOT_DONE(HttpStatus.CONFLICT, "PREPARE_NOT_DONE", "아직 질문을 읽고 있어요. 잠시 후 다시 시도해 주세요."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "ALREADY_SUBMITTED", "이미 다른 방법으로 학습을 시작한 대화예요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
