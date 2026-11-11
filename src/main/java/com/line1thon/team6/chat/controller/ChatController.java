package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.ChatCreateRequest;
import com.line1thon.team6.chat.dto.ChatCreateResponse;
import com.line1thon.team6.chat.dto.ChatDetailResponse;
import com.line1thon.team6.chat.dto.ChatListResponse;
import com.line1thon.team6.chat.dto.PrepareRetryResponse;
import com.line1thon.team6.chat.service.ChatService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "채팅", description = "질문 생성, 최근 대화 목록, 채팅 상세 조회, 대화 삭제, 선생성 재시도")
@RestController
@RequestMapping("/api/chats")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /** 질문 생성. 새로 만들면 201, 같은 전송 ID로 이미 있으면 200 */
    @Operation(summary = "질문 생성",
            description = "채팅을 PREPARING으로 저장하고 바로 응답합니다(201). 선생성(칩, 힌트, 기준 답변)은 백그라운드에서 만들어지며 "
                    + "GET /api/chats/{chatId}를 2초마다 조회해 받습니다. 같은 clientRequestId로 다시 보내면 200으로 기존 대화를 돌려줍니다.")
    @PostMapping
    public ResponseEntity<ChatCreateResponse> create(@CurrentSession GuestSession session,
                                                     @Valid @RequestBody ChatCreateRequest request) {
        ChatService.CreateResult result = chatService.create(session, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    /** 최근 대화 목록. 내 대화만 최신순 */
    @Operation(summary = "최근 대화 목록",
            description = "사이드바 최근 대화와 학습 기록에서 함께 씁니다. 내 게스트 토큰의 채팅만 최신순으로 돌려주고, 없으면 빈 배열입니다. "
                    + "category는 UNLOCKED_SELF면 COMPARED, UNLOCKED_ASSISTED면 BASIC, 잠겨 있으면 null입니다. "
                    + "reflection(배운 점)은 아직 기능이 없어 항상 null입니다.")
    @GetMapping
    public ChatListResponse getList(@CurrentSession GuestSession session,
                                    @Parameter(description = "가져올 개수. 기본 20, 50을 넘으면 50개까지만, 1보다 작으면 400")
                                    @RequestParam(required = false) Integer limit) {
        return chatService.getList(session, limit);
    }

    /** 채팅 상세 조회 (새로고침 복구, 선생성 폴링). 남의 대화나 없는 대화는 404 */
    @Operation(summary = "채팅 상세 조회 (새로고침 복구)",
            description = "선생성 상태, 칩, 힌트 단계, 최초 생각, 분석 작업(mode, status, 재시도 횟수)을 한 번에 돌려줘 화면을 복원합니다. "
                    + "기준 답변과 비교 결과는 넣지 않습니다. prepareStatus가 PREPARING이면 이 API를 2초마다 조회합니다.")
    @GetMapping("/{chatId}")
    public ChatDetailResponse getDetail(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.getDetail(session, chatId);
    }

    /** 대화 삭제. 남의 대화나 없는 대화는 404 */
    @Operation(summary = "대화 삭제",
            description = "질문, 코드, 생각, 분석 작업과 결과를 함께 지웁니다(204). 되돌릴 수 없습니다. "
                    + "없거나 다른 세션의 채팅이면 404 CHAT_NOT_FOUND.")
    @DeleteMapping("/{chatId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        chatService.delete(session, chatId);
    }

    /** 선생성 재시도. FAILED일 때만 가능, 결과는 GET /api/chats/{chatId} 폴링으로 확인 */
    @Operation(summary = "질문 선생성 재시도",
            description = "선생성이 FAILED인 채팅을 다시 PREPARING으로 돌립니다(202). 결과는 GET /api/chats/{chatId} 폴링으로 확인합니다. "
                    + "FAILED가 아니면 409 PREPARE_NOT_FAILED.")
    @PostMapping("/{chatId}/prepare/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PrepareRetryResponse retryPrepare(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        return chatService.retryPrepare(session, chatId);
    }
}
