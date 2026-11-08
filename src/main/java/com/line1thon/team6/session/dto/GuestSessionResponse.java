package com.line1thon.team6.session.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.session.entity.GuestSession;

import java.time.OffsetDateTime;

/** POST /api/sessions 응답 */
public record GuestSessionResponse(
        String guestToken,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
        OffsetDateTime createdAt
) {

    public static GuestSessionResponse from(GuestSession session) {
        return new GuestSessionResponse(session.getToken(), KoreaTime.toOffset(session.getCreatedAt()));
    }
}
