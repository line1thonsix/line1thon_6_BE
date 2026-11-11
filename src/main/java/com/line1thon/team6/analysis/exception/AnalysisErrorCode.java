package com.line1thon.team6.analysis.exception;

import com.line1thon.team6.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 기초 학습, 분석 조회·재시도의 오류 (docs/error-codes.md의 API별 오류). 문구는 임시, 프론트와 정하면 바꾼다 */
@Getter
@RequiredArgsConstructor
public enum AnalysisErrorCode implements ErrorCode {

    BASIC_MODE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "BASIC_MODE_NOT_ALLOWED", "힌트를 두 단계 모두 본 뒤에 기초 학습을 할 수 있어요."),
    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS_NOT_FOUND", "아직 생각을 제출하지 않은 대화예요."),
    NOT_RETRYABLE(HttpStatus.CONFLICT, "NOT_RETRYABLE", "분석이 진행 중이거나 이미 끝났어요."),
    RETRY_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "RETRY_LIMIT_EXCEEDED", "다시 시도할 수 있는 횟수를 모두 썼어요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
