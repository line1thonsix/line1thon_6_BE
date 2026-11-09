package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.dto.ChatCreateResponse;
import com.line1thon.team6.chat.dto.ChatDetailResponse;
import com.line1thon.team6.chat.dto.PrepareRetryResponse;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 질문 생성. 새로 만들면 201, 같은 전송 ID로 이미 있으면 200 */
    @PostMapping
    public ResponseEntity<ChatCreateResponse> create(@CurrentSession GuestSession session,
                                                     @Valid @RequestBody ChatCreateRequest request) {
        ChatService.CreateResult result = chatService.create(session, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    /** 채팅 상세 조회 (새로고침 복구, 선생성 폴링). 남의 대화나 없는 대화는 404 */
    @GetMapping("/{chatId}")
    public ChatDetailResponse getDetail(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.getDetail(session, chatId);
    }

    /** 선생성 재시도. FAILED일 때만 가능, 결과는 GET /api/chats/{chatId} 폴링으로 확인 */
    @PostMapping("/{chatId}/prepare/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PrepareRetryResponse retryPrepare(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.retryPrepare(session, chatId);
    }
}
