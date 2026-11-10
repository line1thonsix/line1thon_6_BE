package com.line1thon.team6.ai.analysis;

import java.util.regex.Pattern;

/**
 * user_quote가 사용자 생각에 그대로 있는지 확인한다.
 * 글자는 한 자도 바뀌면 안 되지만, 줄바꿈·연속 공백 차이는 같은 문장으로 본다 (모델이 줄바꿈을 공백으로 바꾸는 경우).
 */
final class QuoteMatcher {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private QuoteMatcher() {
    }

    static boolean contains(String thought, String quote) {
        if (thought == null || quote == null) {
            return false;
        }
        String normalizedQuote = normalize(quote);
        return !normalizedQuote.isEmpty() && normalize(thought).contains(normalizedQuote);
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip();
    }
}
