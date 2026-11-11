package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import com.line1thon.team6.ai.service.AiJsonText;
import com.line1thon.team6.ai.service.AiOutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기초 설명 출력 검증 (AI 전달 명세 R2 "3. 기초 학습 AI 출력", R5).
 * 네 키가 모두 비어 있지 않은 문자열이어야 한다. 문제는 한 번에 모아 수정 지시로 다시 부른다.
 */
@Component
@RequiredArgsConstructor
public class BasicOutputParser implements AiOutputParser<BasicExplanation> {

    private final JsonMapper jsonMapper;

    @Override
    public BasicExplanation parse(String text) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(AiJsonText.stripCodeFence(text));
        } catch (JacksonException e) {
            throw new AiOutputInvalidException(List.of("JSON으로 읽을 수 없습니다"));
        }
        if (root == null || !root.isObject()) {
            throw new AiOutputInvalidException(List.of("최상위 값이 JSON 객체가 아닙니다"));
        }
        List<String> problems = new ArrayList<>();
        Map<String, String> values = new HashMap<>();
        for (String field : BasicPrompt.FIELDS) {
            JsonNode node = root.get(field);
            if (node == null || node.isNull()) {
                problems.add(field + " 키가 없습니다");
            } else if (!node.isString() || node.asString().isBlank()) {
                problems.add(field + "는 비어 있지 않은 문자열이어야 합니다");
            } else {
                // 예시 코드는 들여쓰기가 의미 있어서 앞쪽 공백을 지우지 않는다
                values.put(field, field.equals("example_code") ? node.asString().stripTrailing() : node.asString().strip());
            }
        }
        if (!problems.isEmpty()) {
            throw new AiOutputInvalidException(problems);
        }
        return new BasicExplanation(values.get("summary"), values.get("example_code"), values.get("observe"),
                values.get("change"));
    }
}
