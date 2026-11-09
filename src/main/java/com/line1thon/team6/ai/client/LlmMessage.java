package com.line1thon.team6.ai.client;

/** 대화의 메시지 하나 */
public record LlmMessage(Role role, String text) {

    public enum Role {
        USER,
        MODEL
    }

    public static LlmMessage user(String text) {
        return new LlmMessage(Role.USER, text);
    }

    public static LlmMessage model(String text) {
        // 빈 메시지는 제공사가 거절하므로 자리 표시를 넣는다
        return new LlmMessage(Role.MODEL, text == null || text.isBlank() ? "(빈 응답)" : text);
    }
}
