package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.ai.exception.AiException;

/**
 * API 키가 없을 때 쓰는 가짜 클라이언트. 실제 AI 없이 선생성 흐름을 끝까지 확인할 수 있게 고정 JSON을 돌려준다.
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

    @Override
    public LlmResponse generate(LlmRequest request) {
        return switch (request.callType()) {
            case PREPARE -> new LlmResponse(PREPARE_OUTPUT, null, null, "STOP");
            // 비교 분석·기초 설명은 해당 기능을 만들 때 추가한다
            case COMPARE, BASIC -> throw new AiException(AiErrorCode.AI_API_ERROR,
                    "가짜 클라이언트가 지원하지 않는 호출: " + request.callType());
        };
    }

    @Override
    public String model() {
        return MODEL;
    }
}
