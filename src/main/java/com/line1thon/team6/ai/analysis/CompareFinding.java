package com.line1thon.team6.ai.analysis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 비교 분석 항목 하나. analysis_job.findings에 AI가 준 순서대로 저장하고, GET /analysis의 findings로 그대로 응답한다.
 * AI 출력과의 대응은 docs/ai/output-schemas.md 2번.
 *
 * @param category         COMMON / COMPLEMENT / UNIQUE / CONFLICT / UNDETERMINED
 * @param userQuote        사용자 생각의 원문 인용. 인용이 없는 항목이면 null
 * @param aiPerspective    AI의 관점 (ai_view, conflict). 없는 분류면 null
 * @param detail           근거 (reason, how_to_check)
 * @param followUpQuestion 판단 보류일 때만
 */
public record CompareFinding(
        String category,
        String title,
        String userQuote,
        String aiPerspective,
        String detail,
        String followUpQuestion
) {

    /** JSON 컬럼에 저장할 형태. 값이 없는 필드도 null로 넣어 항목마다 모양을 같게 한다 */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("category", category);
        map.put("title", title);
        map.put("userQuote", userQuote);
        map.put("aiPerspective", aiPerspective);
        map.put("detail", detail);
        map.put("followUpQuestion", followUpQuestion);
        return map;
    }
}
