package com.line1thon.team6.ai.client;

import com.line1thon.team6.ai.entity.AiCallType;

import java.util.List;
import java.util.Map;

/**
 * LLM 요청 하나.
 *
 * @param callType        가짜 클라이언트가 고정 응답을 고를 때 쓴다
 * @param systemPrompt    역할과 규칙
 * @param messages        대화. 형식 오류로 다시 부를 때는 이전 응답과 수정 지시가 뒤에 붙는다
 * @param responseSchema  출력 JSON 스키마 (Gemini responseSchema 형식)
 * @param maxOutputTokens 작업별 최대 출력 토큰 (R4)
 */
public record LlmRequest(
        AiCallType callType,
        String systemPrompt,
        List<LlmMessage> messages,
        Map<String, Object> responseSchema,
        int maxOutputTokens
) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }

    /** 이전 응답과 수정 지시를 붙인 새 요청 */
    public LlmRequest followUp(String previousOutput, String correction) {
        List<LlmMessage> next = new java.util.ArrayList<>(messages);
        next.add(LlmMessage.model(previousOutput));
        next.add(LlmMessage.user(correction));
        return new LlmRequest(callType, systemPrompt, next, responseSchema, maxOutputTokens);
    }
}
