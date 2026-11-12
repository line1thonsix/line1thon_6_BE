package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import com.line1thon.team6.ai.service.AiJsonText;
import com.line1thon.team6.ai.service.AiOutputParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 선생성 출력 검증 (docs/ai/output-schemas.md 1번, R2).
 * 문제를 한 번에 모두 모아서, 다시 부를 때 모델이 한 번에 고칠 수 있게 한다.
 */
@Component
@RequiredArgsConstructor
public class PrepareOutputParser implements AiOutputParser<PrepareOutput> {

    private final JsonMapper jsonMapper;

    @Override
    public PrepareOutput parse(String text) {
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
        List<String> choices = stringList(root, "choices", PreparePrompt.MIN_CHOICE_COUNT,
                PreparePrompt.MAX_CHOICE_COUNT, problems);
        List<String> hints = stringList(root, "hints", PreparePrompt.HINT_COUNT, PreparePrompt.HINT_COUNT, problems);
        String referenceAnswer = nonBlankString(root, "reference_answer", problems);
        if (!problems.isEmpty()) {
            throw new AiOutputInvalidException(problems);
        }
        return new PrepareOutput(choices, hints, referenceAnswer);
    }

    private static List<String> stringList(JsonNode root, String key, int minSize, int maxSize,
                                           List<String> problems) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            problems.add(key + " 키가 없습니다");
            return List.of();
        }
        if (!node.isArray()) {
            problems.add(key + "는 문자열 배열이어야 합니다");
            return List.of();
        }
        if (node.size() < minSize || node.size() > maxSize) {
            String count = minSize == maxSize ? "정확히 " + minSize : minSize + "~" + maxSize;
            problems.add(key + "는 " + count + "개여야 합니다 (현재 " + node.size() + "개)");
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode item = node.get(i);
            if (!item.isString() || item.asString().isBlank()) {
                problems.add(key + "[" + i + "]는 비어 있지 않은 문자열이어야 합니다");
            } else {
                values.add(item.asString().strip());
            }
        }
        return values;
    }

    private static String nonBlankString(JsonNode root, String key, List<String> problems) {
        JsonNode node = root.get(key);
        if (node == null || node.isNull()) {
            problems.add(key + " 키가 없습니다");
            return null;
        }
        if (!node.isString() || node.asString().isBlank()) {
            problems.add(key + "는 비어 있지 않은 문자열이어야 합니다");
            return null;
        }
        return node.asString().strip();
    }
}
