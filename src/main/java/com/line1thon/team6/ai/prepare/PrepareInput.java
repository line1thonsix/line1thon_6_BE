package com.line1thon.team6.ai.prepare;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.PrepareStatus;
import com.line1thon.team6.chat.entity.QuestionType;

/** 선생성에 필요한 채팅 내용. 트랜잭션 안에서 엔티티를 읽어 만든다 (지연 로딩된 세션 id 포함) */
public record PrepareInput(
        Long chatId,
        Long guestSessionId,
        QuestionType type,
        String exampleId,
        String question,
        String code,
        PrepareStatus prepareStatus
) {

    public static PrepareInput from(Chat chat) {
        return new PrepareInput(chat.getId(), chat.getGuestSession().getId(), chat.getType(), chat.getExampleId(),
                chat.getQuestion(), chat.getCode(), chat.getPrepareStatus());
    }
}
