package com.line1thon.team6.ai.prepare;

import java.util.List;

/** 선생성 결과: 칩 1~3개, 힌트 2단계, 기준 답변 */
public record PrepareOutput(List<String> choices, List<String> hints, String referenceAnswer) {
}
