package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.client.LlmMessage;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.entity.AiCallType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.line1thon.team6.ai.service.JsonSchemas.array;
import static com.line1thon.team6.ai.service.JsonSchemas.object;
import static com.line1thon.team6.ai.service.JsonSchemas.string;

/**
 * 비교 분석 프롬프트. 출력 형식은 AI 담당 전달 명세 R2 (모든 항목에 title, judgment_hold는 배열).
 * 역할·규칙과 입력은 R1 원문 리소스를 사용한다.
 */
final class ComparePrompt {

    /** ai_usage_log.prompt_version */
    static final String VERSION = "comparison_v1.1.0";

    /** 작업별 최대 출력 토큰 (R4) */
    static final int MAX_OUTPUT_TOKENS = 2500;

    /** 분류별 최대 항목 수. 화면에 너무 많이 나오지 않게 제한한다 */
    static final int MAX_ITEMS_PER_CATEGORY = 5;

    static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        for (CompareCategory category : CompareCategory.values()) {
            Map<String, Map<String, Object>> fields = new LinkedHashMap<>();
            for (String field : category.fields()) {
                fields.put(field, string());
            }
            properties.put(category.key(), array(object(fields), 0, MAX_ITEMS_PER_CATEGORY));
        }
        SCHEMA = object(properties);
    }

    private static final AnalysisPromptTemplate TEMPLATE = AnalysisPromptTemplate.load(
            VERSION, "[질문 유형 및 비교 관점]", "[분석 규칙]");
    private static final String SYSTEM = TEMPLATE.system("COMPARISON_SCHEMA", SCHEMA);

    private ComparePrompt() {
    }

    static LlmRequest request(AnalysisInput input) {
        return new LlmRequest(AiCallType.COMPARE, SYSTEM, List.of(LlmMessage.user(user(input))), SCHEMA,
                MAX_OUTPUT_TOKENS);
    }

    private static String user(AnalysisInput input) {
        return TEMPLATE.user(Map.of("QUESTION_TYPE", input.type().name(),
                "QUESTION", input.question(),
                "CODE", AnalysisPromptTemplate.codeOrPlaceholder(input.code()),
                "ORIGINAL_THOUGHT", input.thought(),
                "REFERENCE_ANSWER", input.referenceAnswer()));
    }
}
