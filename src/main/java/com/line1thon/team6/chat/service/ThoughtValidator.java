package com.line1thon.team6.chat.service;

import com.line1thon.team6.chat.exception.ChatErrorCode;
import com.line1thon.team6.global.exception.BusinessException;
import org.springframework.stereotype.Component;

/**
 * 생각 제출 검증 (API 명세서 생각 제출, 11/7 회의). 프론트도 같은 규칙으로 버튼을 켠다.
 * AI로 성의를 판정하지 않는다. 길이 상한(2,000자)은 요청 DTO의 @Size가 검사한다.
 */
@Component
public class ThoughtValidator {

    static final int MIN_LENGTH = 30;
    static final int MIN_DISTINCT_CHARS = 8;

    /** @param content 앞뒤 공백을 뺀 생각 */
    public void validate(String content) {
        // 글자 수는 JS length, Java length()와 같은 기준 (API 명세서 공통 규칙)
        if (content.length() < MIN_LENGTH) {
            throw new BusinessException(ChatErrorCode.THOUGHT_TOO_SHORT);
        }
        if (distinctNonWhitespaceChars(content) < MIN_DISTINCT_CHARS) {
            throw new BusinessException(ChatErrorCode.THOUGHT_INVALID);
        }
    }

    /** 공백을 뺀 서로 다른 문자 수. 이모지처럼 두 칸짜리 문자도 한 글자로 센다 */
    private static long distinctNonWhitespaceChars(String content) {
        return content.codePoints()
                .filter(c -> !Character.isWhitespace(c))
                .distinct()
                .count();
    }
}
