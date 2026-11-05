package com.line1thon.team6.global.exception;

import com.line1thon.team6.global.common.RequestIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 예외를 공통 오류 형식 {@link ErrorResponse}로 바꿔 응답한다.
 * 로그에는 질문, 코드, 생각, AI 응답 원문을 남기지 않는다 (API 명세서 공통 규칙).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** 서비스에서 던진 정해진 오류 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        return toResponse(e.getErrorCode(), e.getMessage());
    }

    /** 예상하지 못한 오류 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리하지 못한 예외 [{}]", RequestIdFilter.current(), e);
        return toResponse(GlobalErrorCode.INTERNAL_ERROR, GlobalErrorCode.INTERNAL_ERROR.getMessage());
    }

    /** @Valid 검증 실패: 첫 번째 필드 오류의 메시지를 보낸다 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(GlobalErrorCode.INVALID_INPUT.getMessage());
        ErrorResponse body = new ErrorResponse(
                GlobalErrorCode.INVALID_INPUT.getCode(), message, RequestIdFilter.current());
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    /**
     * 그 밖에 Spring MVC가 처리하는 오류 (JSON 형식 오류, 헤더 누락, 타입 불일치, 없는 경로, 허용하지 않는 메서드 등).
     * 상태 코드는 Spring이 정한 값을 그대로 쓰고, 형식만 공통 형식으로 맞춘다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        GlobalErrorCode errorCode = toErrorCode(status);
        if (status.is5xxServerError()) {
            log.error("Spring MVC 처리 중 오류 [{}]", RequestIdFilter.current(), e);
        }
        ErrorResponse errorBody = new ErrorResponse(
                errorCode.getCode(), errorCode.getMessage(), RequestIdFilter.current());
        return ResponseEntity.status(status).headers(headers).body(errorBody);
    }

    /** Spring MVC가 정한 상태 코드를 공통 오류 코드로 바꾼다 */
    private GlobalErrorCode toErrorCode(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return GlobalErrorCode.INTERNAL_ERROR;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return GlobalErrorCode.API_NOT_FOUND;
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return GlobalErrorCode.METHOD_NOT_ALLOWED;
        }
        return GlobalErrorCode.INVALID_INPUT;
    }

    private ResponseEntity<ErrorResponse> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(new ErrorResponse(errorCode.getCode(), message, RequestIdFilter.current()));
    }
}
