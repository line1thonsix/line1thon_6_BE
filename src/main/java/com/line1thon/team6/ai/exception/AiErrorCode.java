package com.line1thon.team6.ai.exception;

/**
 * AI 작업 실패 원인 (docs/error-codes.md의 "AI 작업 오류 코드").
 * API 오류 응답이 아니라 작업 상태(analysis_job.error_code)와 ai_usage_log.error_code에 남는 값이다.
 */
public enum AiErrorCode {
    /** 호출 한 번이 제한 시간(15초) 안에 응답하지 않음 */
    AI_TIMEOUT,
    /** 다시 불러도 응답이 출력 형식에 맞지 않음 */
    AI_INVALID_JSON,
    /** 제공사 API 오류 (인증, 사용량 초과, 서버 오류, 연결 실패) */
    AI_API_ERROR
}
