package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepareOutputParserTest {

    private final PrepareOutputParser parser = new PrepareOutputParser(JsonMapper.builder().build());

    @Test
    void 올바른_출력을_읽고_앞뒤_공백을_지운다() {
        PrepareOutput output = parser.parse("""
                {"choices": [" 칩1 ", "칩2", "칩3"], "hints": ["힌트1", "힌트2"], "reference_answer": " 기준 답변 "}
                """);

        assertThat(output.choices()).containsExactly("칩1", "칩2", "칩3");
        assertThat(output.hints()).containsExactly("힌트1", "힌트2");
        assertThat(output.referenceAnswer()).isEqualTo("기준 답변");
    }

    @Test
    void 코드_블록으로_감싼_출력도_읽는다() {
        PrepareOutput output = parser.parse("""
                ```json
                {"choices": ["a", "b", "c"], "hints": ["h1", "h2"], "reference_answer": "r"}
                ```""");

        assertThat(output.choices()).hasSize(3);
    }

    @Test
    void 개수가_틀리면_문제를_모두_모아_알려준다() {
        assertThatThrownBy(() -> parser.parse("""
                {"choices": ["a", "b"], "hints": ["h1", "h2", "h3"], "reference_answer": ""}
                """))
                .isInstanceOfSatisfying(AiOutputInvalidException.class, e -> assertThat(e.getProblems())
                        .containsExactly(
                                "choices는 정확히 3개여야 합니다 (현재 2개)",
                                "hints는 정확히 2개여야 합니다 (현재 3개)",
                                "reference_answer는 비어 있지 않은 문자열이어야 합니다"));
    }

    @Test
    void 키가_없거나_자료형이_틀리면_형식_오류() {
        assertThatThrownBy(() -> parser.parse("""
                {"choices": "a", "hints": ["h1", 2]}
                """))
                .isInstanceOfSatisfying(AiOutputInvalidException.class, e -> assertThat(e.getProblems())
                        .containsExactly(
                                "choices는 문자열 배열이어야 합니다",
                                "hints[1]는 비어 있지 않은 문자열이어야 합니다",
                                "reference_answer 키가 없습니다"));
    }

    @Test
    void JSON이_아니거나_잘린_출력은_형식_오류() {
        assertThatThrownBy(() -> parser.parse("{\"choices\": [\"a\", \"b\""))
                .isInstanceOf(AiOutputInvalidException.class);
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(AiOutputInvalidException.class);
        assertThatThrownBy(() -> parser.parse("[1, 2]"))
                .isInstanceOf(AiOutputInvalidException.class);
    }
}
