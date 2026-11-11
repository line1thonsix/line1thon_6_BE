package com.line1thon.team6.ai.analysis;

/**
 * 기초 설명 (AI 전달 명세 R2 "3. 기초 학습 AI 출력", Figma 10번 화면의 네 칸).
 * analysis_job.basic_explanation에 이 모양의 JSON 문자열로 저장하고, GET /analysis의 basicExplanation 객체로 그대로 응답한다.
 *
 * @param summary     핵심 개념 요약
 * @param exampleCode 개념을 이해할 수 있는 짧은 예시 코드
 * @param observe     먼저 관찰해야 할 부분과 확인 방법
 * @param change      그다음 직접 바꾸거나 시도해 볼 내용
 */
public record BasicExplanation(String summary, String exampleCode, String observe, String change) {
}
