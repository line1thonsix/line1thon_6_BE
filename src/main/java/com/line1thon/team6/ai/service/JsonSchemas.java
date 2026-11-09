package com.line1thon.team6.ai.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Gemini responseSchema(OpenAPI 스키마의 일부)를 만드는 도우미 */
public final class JsonSchemas {

    private JsonSchemas() {
    }

    public static Map<String, Object> string() {
        return Map.of("type", "STRING");
    }

    public static Map<String, Object> array(Map<String, Object> items, int minItems, int maxItems) {
        return Map.of("type", "ARRAY", "items", items, "minItems", minItems, "maxItems", maxItems);
    }

    /** 모든 속성을 필수로 하고, 출력 순서를 선언 순서로 고정한다 */
    public static Map<String, Object> object(Map<String, Map<String, Object>> properties) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        schema.put("required", List.copyOf(properties.keySet()));
        schema.put("propertyOrdering", List.copyOf(properties.keySet()));
        return schema;
    }
}
