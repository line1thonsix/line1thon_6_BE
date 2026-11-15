package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.client.LlmMessage;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.entity.AiCallType;
import com.line1thon.team6.ai.service.PromptGuidelines;

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

    /** AI 담당 전달 원문 리소스명 */
    private static final String SOURCE_VERSION = "comparison_v1.1.0";

    /** ai_usage_log.prompt_version. 이전 노션 복사본과 전달 파일 반영본을 구분한다 */
    static final String VERSION = SOURCE_VERSION + "-r3";

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
            SOURCE_VERSION, "[질문 유형 및 비교 관점]", "[분석 규칙]");
    /** QA 결과로 덧붙인 분류 규칙 (원문 리소스는 그대로 둔다) */
    private static final String QA_RULES = """
            [분류 보완 규칙]
            - 모호하다는 이유만으로 error로 분류하지 않는다. error는 명백한 사실 오류에만 쓴다.
            - 근거가 있는 부분은 common 등으로 인정하고, 불명확한 부분만 judgment_hold로 둔다.
            - 판단할 수 있는 내용까지 보류하지 않는다. 사용자의 의도를 임의로 만들어 내지 않는다.
            - judgment_hold의 follow_up_question은 짧은 구체화 질문 하나로 쓴다.""";

    private static final String SYSTEM = PromptGuidelines.insertBeforeOutputRules(
            TEMPLATE.system("COMPARISON_SCHEMA", SCHEMA), QA_RULES + "\n\n" + PromptGuidelines.COMMON);

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
