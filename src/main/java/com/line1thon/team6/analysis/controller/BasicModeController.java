package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.analysis.dto.BasicModeResponse;
import com.line1thon.team6.analysis.service.BasicModeService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "힌트·기초학습", description = "힌트 요청, 기초 학습 모드 전환")
@RestController
@RequestMapping("/api/chats/{chatId}/basic-mode")
@RequiredArgsConstructor
public class BasicModeController {

    private final BasicModeService basicModeService;

    /** 기초 학습 시작. 바디 없음. 새로 시작하면 202, 이미 시작한 대화면 200으로 기존 작업. 결과는 GET /analysis 폴링 */
    @Operation(summary = "기초 학습 모드 전환",
            description = "힌트 2단계를 본 뒤(hintLevel = 2) 기초 설명(BASIC) 작업을 시작합니다(202). 결과는 GET /analysis로 조회하고, "
                    + "설명이 만들어지면 UNLOCKED_ASSISTED로 풀립니다. 이미 시작했으면 200, 힌트 2단계 전이면 400 BASIC_MODE_NOT_ALLOWED.")
    @PostMapping
    public ResponseEntity<BasicModeResponse> start(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        BasicModeService.StartResult result = basicModeService.start(session, chatId);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.response());
    }
}
