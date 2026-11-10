package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BasicOutputParserTest {

    private final BasicOutputParser parser = new BasicOutputParser(JsonMapper.builder().build());

    @Test
    void 네_칸을_읽고_예시_코드의_들여쓰기는_남긴다() {
        BasicExplanation explanation = parser.parse("""
                {"summary": " 요약 ", "example_code": "  if (x) {\\n    run();\\n  }\\n", "observe": "관찰", "change": "변경"}
                """);

        assertThat(explanation).isEqualTo(new BasicExplanation("요약", "  if (x) {\n    run();\n  }", "관찰", "변경"));
    }

    @Test
    void 키_누락과_빈_값을_한_번에_모은다() {
        assertThatThrownBy(() -> parser.parse("""
                {"summary": "", "example_code": 1, "observe": "관찰"}
                """))
                .isInstanceOfSatisfying(AiOutputInvalidException.class, e -> assertThat(e.getProblems())
                        .containsExactly(
                                "summary는 비어 있지 않은 문자열이어야 합니다",
                                "example_code는 비어 있지 않은 문자열이어야 합니다",
                                "change 키가 없습니다"));
    }

    @Test
    void JSON이_아니면_형식_오류이고_코드_블록은_벗겨서_읽는다() {
        assertThatThrownBy(() -> parser.parse("설명입니다")).isInstanceOf(AiOutputInvalidException.class);
        assertThat(parser.parse("```json\n{\"summary\": \"a\", \"example_code\": \"b\", \"observe\": \"c\", \"change\": \"d\"}\n```")
                .change()).isEqualTo("d");
    }
}
