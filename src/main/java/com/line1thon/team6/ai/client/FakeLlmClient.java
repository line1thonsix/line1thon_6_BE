package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * API 키가 없을 때 쓰는 가짜 클라이언트. 실제 AI 없이 선생성·비교 분석 흐름을 끝까지 확인할 수 있게 고정 JSON을 돌려준다.
 * 출력은 실제 응답과 같은 검증을 거친다.
 */
public class FakeLlmClient implements LlmClient {

    public static final String MODEL = "fake";

    static final String PREPARE_OUTPUT = """
            {
              "choices": ["값이 아직 준비되지 않은 시점", "함수가 실행되는 순서", "초기값의 형태"],
              "hints": ["처음 화면을 그릴 때 이 값은 무엇일까요?", "데이터가 도착하기 전과 후를 나눠서 생각해 보세요."],
              "reference_answer": "가짜 AI 응답입니다. GEMINI_API_KEY를 설정하면 실제 기준 답변이 생성됩니다."
            }
            """;

    private static final String THOUGHT_START = "<thought>\n";
    private static final String THOUGHT_END = "\n</thought>";
    private static final int QUOTE_LENGTH = 20;

    private final JsonMapper jsonMapper;

    public FakeLlmClient(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public LlmResponse generate(LlmRequest request) {
        return switch (request.callType()) {
            case PREPARE -> new LlmResponse(PREPARE_OUTPUT, null, null, "STOP");
            case COMPARE -> new LlmResponse(compareOutput(request), null, null, "STOP");
            // 기초 설명은 해당 기능을 만들 때 추가한다
            case BASIC -> throw new AiException(AiErrorCode.AI_API_ERROR,
                    "가짜 클라이언트가 지원하지 않는 호출: " + request.callType());
        };
    }

    /** 인용 검증을 통과하도록 사용자 생각의 앞부분을 그대로 인용한다 */
    private String compareOutput(LlmRequest request) {
        String quote = quoteFrom(request.messages().getFirst().text());
        Map<String, Object> output = Map.of(
                "common", List.of(Map.of("title", "가짜 공통점", "user_quote", quote,
                        "ai_view", "가짜 AI 응답입니다.", "reason", "GEMINI_API_KEY를 설정하면 실제 비교 결과가 생성됩니다.")),
                "supplement", List.of(Map.of("title", "가짜 보완점", "user_quote", "",
                        "ai_view", "추가로 알아 두면 좋은 내용 (가짜)", "reason", "가짜 AI 응답입니다.")),
                "unique", List.of(),
                "error", List.of(),
                "judgment_hold", List.of());
        return jsonMapper.writeValueAsString(output);
    }

    private static String quoteFrom(String userPrompt) {
        int start = userPrompt.indexOf(THOUGHT_START);
        int end = userPrompt.indexOf(THOUGHT_END, start + 1);
        if (start < 0 || end < 0) {
            return "";
        }
        String thought = userPrompt.substring(start + THOUGHT_START.length(), end).strip();
        return thought.substring(0, Math.min(QUOTE_LENGTH, thought.length())).strip();
    }

    @Override
    public String model() {
        return MODEL;
    }
}
