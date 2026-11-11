package com.line1thon.team6.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.global.common.KoreaTime;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * GET /api/chats 응답 (사이드바 최근 대화, 학습 기록 화면).
 * 질문 전체와 코드, 생각, 분석 결과는 넣지 않는다. 자세한 내용은 GET /api/chats/{chatId}로 조회한다.
 */
public record ChatListResponse(List<Item> items) {

    /** 질문 미리보기 길이 (API 명세서: 질문 앞 40자) */
    static final int PREVIEW_LENGTH = 40;

    public static ChatListResponse from(List<Chat> chats) {
        return new ChatListResponse(chats.stream().map(Item::from).toList());
    }

    /**
     * @param category   학습 기록 구분. UNLOCKED_SELF면 COMPARED, UNLOCKED_ASSISTED면 BASIC, 잠겨 있으면 null
     * @param reflection 배운 점 한 줄. 회고 기능을 만들지 않기로 해서(미구현) 지금은 항상 null
     */
    public record Item(
            Long chatId,
            QuestionType type,
            String questionPreview,
            ChatStatus status,
            Category category,
            String reflection,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
            OffsetDateTime createdAt
    ) {

        static Item from(Chat chat) {
            return new Item(
                    chat.getId(),
                    chat.getType(),
                    preview(chat.getQuestion()),
                    chat.getStatus(),
                    Category.of(chat.getStatus()),
                    null,
                    KoreaTime.toOffset(chat.getCreatedAt()));
        }
    }

    /** 학습 기록 구분 (서버가 잠금 상태로 계산) */
    public enum Category {
        COMPARED, BASIC;

        static Category of(ChatStatus status) {
            return switch (status) {
                case UNLOCKED_SELF -> COMPARED;
                case UNLOCKED_ASSISTED -> BASIC;
                case LOCKED -> null;
            };
        }
    }

    /**
     * 질문 앞 40자. 사이드바에 한 줄로 보이도록 줄바꿈과 연속 공백은 공백 하나로 바꾼 뒤 자른다.
     * 이모지 같은 2칸짜리 문자(서로게이트 쌍)가 40번째에서 잘리면 깨지므로 그 문자는 뺀다.
     */
    static String preview(String question) {
        String oneLine = question.strip().replaceAll("\\s+", " ");
        if (oneLine.length() <= PREVIEW_LENGTH) {
            return oneLine;
        }
        int end = PREVIEW_LENGTH;
        if (Character.isHighSurrogate(oneLine.charAt(end - 1))) {
            end--;
        }
        return oneLine.substring(0, end);
    }
}
