package com.line1thon.team6.chat.dto;

import com.line1thon.team6.chat.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * POST /api/chats 요청.
 * 문자 수는 앞뒤 공백을 뺀 뒤 센다 (API 명세서 공통 규칙). 그래서 생성자에서 먼저 공백을 지운다.
 */
public record ChatCreateRequest(
        @NotNull(message = "전송 ID가 필요해요.")
        UUID clientRequestId,

        @NotNull(message = "질문 유형을 골라 주세요.")
        QuestionType type,

        @NotBlank(message = "질문을 입력해 주세요.")
        @Size(max = 2000, message = "질문은 2,000자까지 쓸 수 있어요.")
        String question,

        @Size(max = 6000, message = "코드는 6,000자까지 붙여 넣을 수 있어요.")
        String code,

        @Pattern(regexp = "EXAMPLE_DEBUGGING|EXAMPLE_CONCEPT|EXAMPLE_PROBLEM_SOLVING",
                message = "예시 질문 값이 올바르지 않아요.")
        String exampleId
) {

    public ChatCreateRequest {
        question = question == null ? null : question.strip();
        code = code == null || code.isBlank() ? null : code.strip();
    }
}
