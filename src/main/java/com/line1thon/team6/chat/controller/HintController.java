package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.OpenedHint;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats/{chatId}/hints")
@RequiredArgsConstructor
public class HintController {

    private final ChatService chatService;

    /**
     * 힌트 한 단계 공개. 바디 없음. 항상 200 (이미 2단계면 마지막 힌트를 다시 돌려줌).
     * 선생성 전이면 409 PREPARE_NOT_DONE, 생각 제출·기초 학습 후면 409 ALREADY_SUBMITTED.
     */
    @PostMapping
    public OpenedHint open(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.openNextHint(session, chatId);
    }
}
