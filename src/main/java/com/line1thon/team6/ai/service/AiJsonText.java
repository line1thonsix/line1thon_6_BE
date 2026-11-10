package com.line1thon.team6.ai.service;

/** AI 출력 텍스트 정리 */
public final class AiJsonText {

    private AiJsonText() {
    }

    /** 규칙상 JSON만 출력해야 하지만, ```json 으로 감싸 오는 경우는 벗겨서 읽는다 */
    public static String stripCodeFence(String text) {
        if (text == null) {
            return "";
        }
        String s = text.strip();
        if (s.startsWith("```")) {
            int firstLineEnd = s.indexOf('\n');
            s = firstLineEnd < 0 ? "" : s.substring(firstLineEnd + 1);
            if (s.endsWith("```")) {
                s = s.substring(0, s.length() - 3);
            }
        }
        return s.strip();
    }
}
