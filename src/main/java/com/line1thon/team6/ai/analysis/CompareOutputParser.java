package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;
import com.line1thon.team6.ai.service.AiJsonText;
import com.line1thon.team6.ai.service.AiOutputParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 비교 분석 출력 검증 (docs/ai/output-schemas.md 2번·4번, R2, R5).
 * <ul>
 *   <li>키, 타입, 빈 값 오류는 한 번에 모두 모아 형식 오류로 던진다 → 수정 지시를 붙여 다시 부른다</li>
 *   <li>user_quote가 사용자 생각에 그대로 없는 항목은 그 항목만 버린다 (P6)</li>
 *   <li>버리고 남은 항목이 없으면 형식 오류로 다시 부른다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompareOutputParser {

    private static final String QUOTE = "user_quote";

    private final JsonMapper jsonMapper;

    /** 인용 검증에 사용자 생각이 필요해서, 호출마다 생각을 묶은 파서를 만든다 */
    public AiOutputParser<List<CompareFinding>> forThought(String thought) {
        return text -> parse(text, thought);
    }

    List<CompareFinding> parse(String text, String thought) {
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
        List<String> dropped = new ArrayList<>();
        List<CompareFinding> findings = new ArrayList<>();
        for (CompareCategory category : CompareCategory.values()) {
            JsonNode items = root.get(category.key());
            if (items == null || items.isNull()) {
                problems.add(category.key() + " 키가 없습니다 (항목이 없으면 빈 배열)");
                continue;
            }
            if (!items.isArray()) {
                problems.add(category.key() + "는 배열이어야 합니다");
                continue;
            }
            for (int i = 0; i < items.size(); i++) {
                String path = category.key() + "[" + i + "]";
                Map<String, String> values = readItem(category, items.get(i), path, problems);
                if (values == null) {
                    continue;
                }
                String quote = values.get(QUOTE);
                if (!quote.isEmpty() && !QuoteMatcher.contains(thought, quote)) {
                    dropped.add(path);
                    continue;
                }
                findings.add(toFinding(category, values));
            }
        }
        if (!problems.isEmpty()) {
            throw new AiOutputInvalidException(problems);
        }
        if (findings.isEmpty()) {
            throw new AiOutputInvalidException(List.of(dropped.isEmpty()
                    ? "항목이 하나도 없습니다. 다섯 분류 중 해당하는 항목을 1개 이상 작성하세요"
                    : "user_quote가 사용자가 제출한 최초 생각에 그대로 있지 않아 모든 항목을 버렸습니다 " + dropped
                    + ". user_quote는 사용자가 제출한 최초 생각의 문장을 한 글자도 바꾸지 않고 인용하세요"));
        }
        if (!dropped.isEmpty()) {
            // 인용 원문은 사용자 내용이라 로그에 남기지 않는다
            log.info("원문에 없는 인용 항목을 버림: dropped={} kept={}", dropped, findings.size());
        }
        return findings;
    }

    /** 항목의 문자열 필드를 읽는다. 문제가 있으면 problems에 넣고 null */
    private static Map<String, String> readItem(CompareCategory category, JsonNode item, String path,
                                                List<String> problems) {
        if (item == null || !item.isObject()) {
            problems.add(path + "는 객체여야 합니다");
            return null;
        }
        Map<String, String> values = new HashMap<>();
        boolean valid = true;
        for (String field : category.fields()) {
            JsonNode node = item.get(field);
            if (node == null || !node.isString()) {
                problems.add(path + "." + field + "는 문자열이어야 합니다");
                valid = false;
                continue;
            }
            String value = node.asString().strip();
            boolean mayBeEmpty = field.equals(QUOTE) && !category.quoteRequired();
            if (value.isEmpty() && !mayBeEmpty) {
                problems.add(path + "." + field + "가 비어 있습니다");
                valid = false;
                continue;
            }
            values.put(field, value);
        }
        return valid ? values : null;
    }

    private static CompareFinding toFinding(CompareCategory category, Map<String, String> v) {
        String quote = v.get(QUOTE).isEmpty() ? null : v.get(QUOTE);
        return switch (category) {
            case COMMON, SUPPLEMENT -> new CompareFinding(category.category(), v.get("title"), quote,
                    v.get("ai_view"), v.get("reason"), null);
            case UNIQUE -> new CompareFinding(category.category(), v.get("title"), quote,
                    null, v.get("reason"), null);
            case ERROR -> new CompareFinding(category.category(), v.get("title"), quote,
                    v.get("conflict"), v.get("how_to_check"), null);
            case JUDGMENT_HOLD -> new CompareFinding(category.category(), v.get("title"), quote,
                    null, v.get("reason"), v.get("follow_up_question"));
        };
    }
}
