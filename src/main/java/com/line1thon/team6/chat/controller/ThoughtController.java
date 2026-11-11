package com.line1thon.team6.chat.controller;

import com.line1thon.team6.chat.dto.ThoughtSubmitRequest;
import com.line1thon.team6.chat.dto.ThoughtSubmitResponse;
import com.line1thon.team6.chat.service.ThoughtService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "생각·잠금", description = "생각 제출과 비교 분석 시작")
@RestController
@RequestMapping("/api/chats/{chatId}/thoughts")
@RequiredArgsConstructor
public class ThoughtController {

    private final ThoughtService thoughtService;

    /** 생각 제출. 분석을 시작하면 202, 이미 제출한 대화면 200으로 기존 작업. 결과는 GET /analysis 폴링 */
    @Operation(summary = "생각 제출 (잠금 해제)",
            description = "30~2,000자, 공백 제외 서로 다른 문자 8개 이상을 검증하고 최초 작성본을 저장한 뒤 비교 분석(COMPARE)을 시작합니다(202). "
                    + "잠금은 분석이 끝날 때 UNLOCKED_SELF로 풀립니다. 이미 제출했으면 200으로 기존 작업, 기초 학습을 시작했으면 409 ALREADY_SUBMITTED.")
    @PostMapping
    public ResponseEntity<ThoughtSubmitResponse> submit(@CurrentSession GuestSession session,
                                                        @PathVariable Long chatId,
                                                        @Valid @RequestBody ThoughtSubmitRequest request) {
        ThoughtService.SubmitResult result = thoughtService.submit(session, chatId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.response());
    }
}
