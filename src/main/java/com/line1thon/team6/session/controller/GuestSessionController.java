package com.line1thon.team6.session.controller;

import com.line1thon.team6.session.dto.GuestSessionResponse;
import com.line1thon.team6.session.service.GuestSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class GuestSessionController {

    private final GuestSessionService guestSessionService;

    /** 게스트 토큰 발급. 토큰 인증에서 제외된 API (WebConfig) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestSessionResponse create() {
        return guestSessionService.create();
    }
}
