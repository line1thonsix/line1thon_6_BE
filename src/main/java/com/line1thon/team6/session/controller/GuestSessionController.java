package com.line1thon.team6.session.controller;

import com.line1thon.team6.session.auth.CurrentSession;
import com.line1thon.team6.session.dto.GuestSessionResponse;
import com.line1thon.team6.session.entity.GuestSession;
import com.line1thon.team6.session.service.GuestSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "세션", description = "게스트 토큰 발급, 전체 데이터 삭제")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class GuestSessionController {

    private final GuestSessionService guestSessionService;

    /** 게스트 토큰 발급. 토큰 인증에서 제외된 API (WebConfig) */
    @Operation(summary = "게스트 세션 발급",
            description = "회원가입 없이 게스트 토큰을 발급합니다. 토큰이 없을 때만 호출하고, 받은 토큰은 이후 모든 요청의 "
                    + "X-Guest-Token 헤더에 넣습니다. 어떤 요청이든 401 INVALID_TOKEN을 받으면 이 API로 다시 발급합니다. 토큰 없이 호출합니다.")
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestSessionResponse create() {
        return guestSessionService.create();
    }

    /** 전체 데이터 삭제. 토큰이 필요하다 (/api/sessions만 인증 제외라서 /api/sessions/me는 검사함) */
    @Operation(summary = "전체 데이터 삭제",
            description = "내 모든 채팅과 게스트 세션까지 지웁니다(204). 성공하면 기존 토큰은 401 INVALID_TOKEN이 되므로, "
                    + "프론트는 저장된 토큰을 지우고 다음 요청 전에 POST /api/sessions로 새로 발급합니다.")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMe(@CurrentSession GuestSession session) {
        guestSessionService.delete(session);
    }
}
