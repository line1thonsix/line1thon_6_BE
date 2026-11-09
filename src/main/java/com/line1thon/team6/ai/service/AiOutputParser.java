package com.line1thon.team6.ai.service;

import com.line1thon.team6.ai.exception.AiOutputInvalidException;

/** 모델 출력 텍스트를 결과 객체로 바꾸고 형식을 검증한다 */
@FunctionalInterface
public interface AiOutputParser<T> {

    /** @throws AiOutputInvalidException 형식이 틀림 (다시 부를 대상) */
    T parse(String text);
}
