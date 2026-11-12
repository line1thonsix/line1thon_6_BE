package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PreparePromptTest {

    @Test
    void Gemini에_칩_1부터_3개와_힌트_2개_스키마를_보낸다() {
        LlmRequest request = PreparePrompt.request(new PrepareInput(1L, 2L,
                QuestionType.DEBUGGING, null, "왜 오류인가요?", null, PrepareStatus.PREPARING));
        var schema = JsonMapper.builder().build().valueToTree(request.responseSchema());

        assertThat(schema.at("/properties/choices/minItems").asInt()).isEqualTo(1);
        assertThat(schema.at("/properties/choices/maxItems").asInt()).isEqualTo(3);
        assertThat(schema.at("/properties/hints/minItems").asInt()).isEqualTo(2);
        assertThat(schema.at("/properties/hints/maxItems").asInt()).isEqualTo(2);
    }
}
