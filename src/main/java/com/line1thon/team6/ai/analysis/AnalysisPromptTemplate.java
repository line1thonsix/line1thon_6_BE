package com.line1thon.team6.ai.analysis;

import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** R1 원문에서 역할·규칙은 system으로, 입력 부분은 user로 분리한다. */
record AnalysisPromptTemplate(String system, String user) {

    static final String SECURITY_RULE = read("security_rules_v1.1.0").strip();
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\{\\{([A-Z_]+)}}}}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    static AnalysisPromptTemplate load(String version, String inputStart, String rulesStart) {
        String source = read(version);
        int start = source.indexOf(inputStart);
        int end = source.indexOf(rulesStart, start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("R1 프롬프트 구획 누락: " + version);
        }
        return new AnalysisPromptTemplate(source.substring(0, start) + source.substring(end),
                source.substring(start, end));
    }

    String system(String schemaVariable, Map<String, Object> schema) {
        return render(system, Map.of("SECURITY_RULE", SECURITY_RULE,
                schemaVariable, JSON.writeValueAsString(schema)));
    }

    String user(Map<String, String> variables) {
        return render(user, variables);
    }

    static String codeOrPlaceholder(String code) {
        return code == null || code.isBlank() ? "(코드 없음)" : code;
    }

    private static String render(String template, Map<String, String> variables) {
        // 삽입한 사용자 원문 속 변수나 $/역슬래시를 재해석하지 않는다.
        return VARIABLE.matcher(template).replaceAll(match -> {
            String value = variables.get(match.group(1));
            if (value == null) {
                throw new IllegalArgumentException("R1 프롬프트 변수 누락: " + match.group(1));
            }
            return Matcher.quoteReplacement(value);
        });
    }

    private static String read(String version) {
        try {
            return new ClassPathResource("prompts/" + version + ".txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("R1 프롬프트를 읽을 수 없음: " + version, e);
        }
    }
}
