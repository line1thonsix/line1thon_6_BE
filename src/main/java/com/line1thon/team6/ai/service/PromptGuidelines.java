package com.line1thon.team6.ai.service;

/**
 * QA 결과로 정한 프롬프트 보완 규칙 (2026-11 QA). AI 담당 원문 리소스는 그대로 두고 백엔드가 덧붙인다.
 * 바꾸면 각 프롬프트의 VERSION도 올린다.
 */
public final class PromptGuidelines {

    private PromptGuidelines() {
    }

    /** 선생성·비교 분석·기초 설명 모두에 붙이는 공통 원칙 */
    public static final String COMMON = """
            [공통 원칙]
            - 원인을 하나로 단정하지 않는다. 정보가 부족하면 부족하다는 사실을 밝힌다.
            - 코드가 없으면 특정 변수나 코드 줄을 지목하지 않는다.
            - 판단하기 어려우면 코드, 로그, 스택 트레이스(StackTrace) 같은 추가 정보를 요청한다.
            - 사실이 확실한 내용만 쓴다. 시간 복잡도나 최적화 효과처럼 헷갈리기 쉬운 내용은 무엇이 무엇의 결과인지 정확히 구분한다.
            - 영어 용어는 한글 뒤 괄호에만 쓴다. 예: 마이크로서비스 구조(MSA)
            - 표준 맞춤법과 정확한 용어를 쓴다. 예: 히프가 아니라 힙, 인증 세션이 아니라 인증 섹션처럼 문맥에 맞는 말""";

    /**
     * 원문 프롬프트의 "[출력 규칙]" 앞에 보완 규칙을 넣는다. 출력 형식(스키마)이 프롬프트 맨 끝에 남게 한다.
     * 표지가 없으면 맨 끝에 붙인다.
     */
    public static String insertBeforeOutputRules(String prompt, String extra) {
        int at = prompt.indexOf(OUTPUT_RULES_MARKER);
        if (at < 0) {
            return prompt + "\n\n" + extra;
        }
        return prompt.substring(0, at) + extra + "\n\n" + prompt.substring(at);
    }

    private static final String OUTPUT_RULES_MARKER = "[출력 규칙]";
}
