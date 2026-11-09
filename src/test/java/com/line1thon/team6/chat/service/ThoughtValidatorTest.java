package com.line1thon.team6.chat.service;

import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThoughtValidatorTest {

    private final ThoughtValidator validator = new ThoughtValidator();

    /** 서로 다른 문자 8개 이상인 문장을 원하는 길이로 */
    private static String sentence(int length) {
        return "첫 렌더링 때 users 값부터 볼게요 ".repeat(100).substring(0, length);
    }

    private static void assertError(String content, ChatErrorCode errorCode) {
        assertThatThrownBy(() -> new ThoughtValidator().validate(content))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }

    @Test
    void 서른_자_이상이고_서로_다른_문자가_여덟_개_이상이면_통과() {
        assertThatCode(() -> validator.validate(sentence(30))).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(sentence(2000))).doesNotThrowAnyException();
    }

    @Test
    void 스물아홉_자면_THOUGHT_TOO_SHORT() {
        assertError(sentence(29), ChatErrorCode.THOUGHT_TOO_SHORT);
    }

    @Test
    void 빈_문자열은_THOUGHT_TOO_SHORT() {
        assertError("", ChatErrorCode.THOUGHT_TOO_SHORT);
    }

    @Test
    void 같은_글자만_반복하면_THOUGHT_INVALID() {
        assertError("ㅋ".repeat(40), ChatErrorCode.THOUGHT_INVALID);
    }

    @Test
    void 공백을_뺀_서로_다른_문자가_일곱_개면_THOUGHT_INVALID() {
        // a~g 일곱 글자 + 공백
        assertError("abcdefg ".repeat(5), ChatErrorCode.THOUGHT_INVALID);
    }

    @Test
    void 공백을_뺀_서로_다른_문자가_여덟_개면_통과() {
        assertThatCode(() -> validator.validate("abcdefgh ".repeat(4))).doesNotThrowAnyException();
    }

    @Test
    void 공백은_서로_다른_문자로_세지_않는다() {
        // 일곱 글자 + 공백, 줄바꿈, 탭 → 공백 종류가 달라도 일곱 개
        assertError("abcdefg \n\t".repeat(4), ChatErrorCode.THOUGHT_INVALID);
    }
}
