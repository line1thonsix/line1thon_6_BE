package com.line1thon.team6.session.entity;

import jakarta.persistence.*;
import com.line1thon.team6.global.common.KoreaTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 헤더 X-Guest-Token 값 (UUID 36자). DB 스키마 문서의 CHAR(36)에 맞춤 */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, unique = true, length = 36)
    private String token;

    /** 나중에 로그인을 붙일 때를 위한 자리. MVP에서는 항상 null */
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public GuestSession(String token) {
        this.token = token;
        this.createdAt = KoreaTime.now();
    }
}
