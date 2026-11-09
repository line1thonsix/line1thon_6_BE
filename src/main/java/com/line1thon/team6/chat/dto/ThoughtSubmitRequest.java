package com.line1thon.team6.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/chats/{chatId}/thoughts 요청.
 * 문자 수는 앞뒤 공백을 뺀 뒤 센다 (API 명세서 공통 규칙). 그래서 생성자에서 먼저 공백을 지운다.
 * 2,000자 초과는 400 INVALID_INPUT, 30자 미만과 서로 다른 문자 수는 ThoughtValidator가 따로 검사한다.
 */
public record ThoughtSubmitRequest(
        @NotNull(message = "생각을 입력해 주세요.")
        @Size(max = 2000, message = "생각은 2,000자까지 쓸 수 있어요.")
        String content,

        @Min(value = 0, message = "칩 번호가 올바르지 않아요.")
        @Max(value = 2, message = "칩 번호가 올바르지 않아요.")
        Integer selectedChoice
) {

    public ThoughtSubmitRequest {
        content = content == null ? null : content.strip();
    }
}
