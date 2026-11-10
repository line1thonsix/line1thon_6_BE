package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.ThoughtSubmitRequest;
import com.line1thon.team6.chat.dto.ThoughtSubmitResponse;
import com.line1thon.team6.chat.service.ThoughtService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats/{chatId}/thoughts")
@RequiredArgsConstructor
public class ThoughtController {

    private final ThoughtService thoughtService;

    /** 생각 제출. 분석을 시작하면 202, 이미 제출한 대화면 200으로 기존 작업. 결과는 GET /analysis 폴링 */
    @PostMapping
    public ResponseEntity<ThoughtSubmitResponse> submit(@CurrentSession GuestSession session,
                                                        @PathVariable Long chatId,
                                                        @Valid @RequestBody ThoughtSubmitRequest request) {
        ThoughtService.SubmitResult result = thoughtService.submit(session, chatId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.response());
    }
}
