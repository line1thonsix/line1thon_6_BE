package com.line1thon.team6.chat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;
import com.line1thon.team6.global.common.KoreaTime;

import java.time.OffsetDateTime;

/** POST /api/chats 응답. 기준 답변, 칩, 힌트는 넣지 않는다 */
public record ChatCreateResponse(
        Long chatId,
        ChatStatus status,
        QuestionType type,
        PrepareStatus prepareStatus,
        boolean secretMasked,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime createdAt
) {

    public static ChatCreateResponse from(Chat chat) {
        return new ChatCreateResponse(chat.getId(), chat.getStatus(), chat.getType(), chat.getPrepareStatus(),
                chat.isSecretMasked(), KoreaTime.toOffset(chat.getCreatedAt()));
    }
}
