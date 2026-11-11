package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.OpenedHint;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "힌트·기초학습", description = "힌트 요청, 기초 학습 모드 전환")
@RestController
@RequestMapping("/api/chats/{chatId}/hints")
@RequiredArgsConstructor
public class HintController {

    private final ChatService chatService;

    /**
     * 힌트 한 단계 공개. 바디 없음. 항상 200 (이미 2단계면 마지막 힌트를 다시 돌려줌).
     * 선생성 전이면 409 PREPARE_NOT_DONE, 생각 제출·기초 학습 후면 409 ALREADY_SUBMITTED.
     */
    @Operation(summary = "힌트 요청",
            description = "선생성해 둔 힌트를 한 단계 공개합니다(hintLevel + 1). 잠금 상태는 바꾸지 않습니다. "
                    + "2단계 이후에는 마지막 힌트를 다시 돌려줍니다. 분석 작업이 있으면 409 ALREADY_SUBMITTED, 선생성 전이면 409 PREPARE_NOT_DONE.")
    @PostMapping
    public OpenedHint open(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.openNextHint(session, chatId);
    }
}
