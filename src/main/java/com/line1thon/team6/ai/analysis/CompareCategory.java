package com.line1thon.team6.ai.analysis;

import java.util.List;

/**
 * 비교 분석의 AI 출력 분류와 저장할 category의 대응 (docs/ai/output-schemas.md 2번, R2).
 * fields는 AI 출력 항목의 키이고, 모두 문자열이다.
 */
enum CompareCategory {

    COMMON("common", "COMMON", true, List.of("title", "user_quote", "ai_view", "reason")),
    SUPPLEMENT("supplement", "COMPLEMENT", false, List.of("title", "user_quote", "ai_view", "reason")),
    UNIQUE("unique", "UNIQUE", true, List.of("title", "user_quote", "reason")),
    ERROR("error", "CONFLICT", true, List.of("title", "user_quote", "conflict", "how_to_check")),
    JUDGMENT_HOLD("judgment_hold", "UNDETERMINED", true,
            List.of("title", "user_quote", "reason", "follow_up_question"));

    private final String key;
    private final String category;
    private final boolean quoteRequired;
    private final List<String> fields;

    CompareCategory(String key, String category, boolean quoteRequired, List<String> fields) {
        this.key = key;
        this.category = category;
        this.quoteRequired = quoteRequired;
        this.fields = fields;
    }

    /** AI 출력의 키 (snake_case) */
    String key() {
        return key;
    }

    /** findings[].category */
    String category() {
        return category;
    }

    /** false면 user_quote가 ""일 수 있다 (관련 발언 없음). AI 전달 명세 R2상 supplement만 해당 */
    boolean quoteRequired() {
        return quoteRequired;
    }

    List<String> fields() {
        return fields;
    }
}
