package com.line1thon.team6.chat.dto;

import com.line1thon.team6.chat.entity.ChatStatus;
import com.line1thon.team6.chat.entity.PrepareStatus;

/** POST /api/chats/{chatId}/prepare/retry 응답. prepareStatus는 항상 PREPARING */
public record PrepareRetryResponse(
        Long chatId,
        ChatStatus status,
        PrepareStatus prepareStatus
) {
}
