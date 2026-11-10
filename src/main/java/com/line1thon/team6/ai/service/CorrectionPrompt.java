package com.line1thon.team6.ai.service;

import java.util.List;
import java.util.stream.Collectors;

/** 형식 오류로 다시 부를 때 붙이는 수정 지시 (AI 담당 전달 명세 R5-3) */
final class CorrectionPrompt {

    private CorrectionPrompt() {
    }

    static String of(List<String> problems) {
        String errors = problems.stream().map(p -> "- " + p).collect(Collectors.joining("\n"));
        return """
                이전 응답이 요구된 JSON 형식 검증에 실패했습니다.

                다음 오류를 수정하여 다시 출력하세요.

                %s

                필수 규칙:
                1. 지정된 모든 필수 키를 포함하세요.
                2. 각 필드의 자료형을 준수하세요.
                3. 배열 필드는 반드시 배열로 반환하세요.
                4. 사용자 원문에 실제로 존재하지 않는 인용문을 만들지 마세요.
                5. 기준 답변이나 입력에 없는 사실을 임의로 추가하지 마세요.
                6. JSON 이외의 설명이나 Markdown을 출력하지 마세요.

                올바른 JSON 객체만 반환하세요.""".formatted(errors);
    }
}
