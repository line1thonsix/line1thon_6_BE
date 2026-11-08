package com.line1thon.team6.global.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * 한국 시간 기준 시각 (API 명세서 공통 규칙: ISO-8601, +09:00).
 * 서버 기본 시간대가 달라도 항상 한국 시간으로 저장하고 응답한다.
 */
public final class KoreaTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KoreaTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /** DB에 저장된 한국 시간을 +09:00 오프셋이 붙은 시각으로 바꾼다 */
    public static OffsetDateTime toOffset(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE).toOffsetDateTime();
    }
}
