package com.line1thon.team6.ai.analysis;

import com.line1thon.team6.ai.client.LlmMessage;
import com.line1thon.team6.ai.client.LlmRequest;
import com.line1thon.team6.ai.entity.AiCallType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.line1thon.team6.ai.service.JsonSchemas.object;
import static com.line1thon.team6.ai.service.JsonSchemas.string;

/**
 * 기초 설명 프롬프트. 출력 형식은 AI 전달 명세 R2 "3. 기초 학습 AI 출력" (summary, example_code, observe, change).
 * 역할·규칙과 입력은 R1 원문 리소스를 사용한다.
 */
final class BasicPrompt {

    /** AI 담당 전달 원문 리소스명 */
    private static final String SOURCE_VERSION = "learning_v1.1.0";

    /** ai_usage_log.prompt_version. 이전 노션 복사본과 전달 파일 반영본을 구분한다 */
    static final String VERSION = SOURCE_VERSION + "-r2";

    /** 작업별 최대 출력 토큰 (R4) */
    static final int MAX_OUTPUT_TOKENS = 1500;

    /** AI 출력 키 (snake_case). 선언 순서가 출력 순서다 */
    static final List<String> FIELDS = List.of("summary", "example_code", "observe", "change");

    static final Map<String, Object> SCHEMA;

    static {
        Map<String, Map<String, Object>> properties = new LinkedHashMap<>();
        FIELDS.forEach(field -> properties.put(field, string()));
        SCHEMA = object(properties);
    }

    private static final AnalysisPromptTemplate TEMPLATE = AnalysisPromptTemplate.load(
            SOURCE_VERSION, "질문 유형:", "[작성 규칙]");
    private static final String SYSTEM = TEMPLATE.system("LEARNING_SCHEMA", SCHEMA);

    private BasicPrompt() {
    }

    static LlmRequest request(AnalysisInput input) {
        return new LlmRequest(AiCallType.BASIC, SYSTEM, List.of(LlmMessage.user(user(input))), SCHEMA,
                MAX_OUTPUT_TOKENS);
    }

    private static String user(AnalysisInput input) {
        return TEMPLATE.user(Map.of("QUESTION_TYPE", input.type().name(),
                "QUESTION", input.question(),
                "CODE", AnalysisPromptTemplate.codeOrPlaceholder(input.code())));
    }
}
