package com.line1thon.team6.analysis.controller;

import com.line1thon.team6.analysis.dto.BasicModeResponse;
import com.line1thon.team6.analysis.service.BasicModeService;
import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.entity.GuestSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats/{chatId}/basic-mode")
@RequiredArgsConstructor
public class BasicModeController {

    private final BasicModeService basicModeService;

    /** 기초 학습 시작. 바디 없음. 새로 시작하면 202, 이미 시작한 대화면 200으로 기존 작업. 결과는 GET /analysis 폴링 */
    @PostMapping
    public ResponseEntity<BasicModeResponse> start(@CurrentSession GuestSession session, @PathVariable Long chatId) {
        BasicModeService.StartResult result = basicModeService.start(session, chatId);
        return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK).body(result.response());
    }
}
