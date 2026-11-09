package com.line1thon.team6.ai.entity;

import com.line1thon.team6.ai.exception.AiErrorCode;
import com.line1thon.team6.global.common.KoreaTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * AI 호출 한 번의 기록. 컬럼은 docs/db-schema.md의 ai_usage_log 테이블을 따른다.
 * 채팅이나 세션을 지워도 기록은 남도록 FK 없이 guest_session_id 숫자만 저장한다.
 * 질문, 코드, 생각, AI 응답 원문은 저장하지 않는다.
 */
@Entity
@Table(indexes = @Index(name = "idx_usage_guest", columnList = "guest_session_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long guestSessionId;

    @Column(nullable = false, length = 40)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private AiCallType callType;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(nullable = false, length = 20)
    private String promptVersion;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private AiCallResult result;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private AiErrorCode errorCode;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static AiUsageLog of(Long guestSessionId, String requestId, AiCallType callType, String model,
                                String promptVersion, Integer inputTokens, Integer outputTokens, Integer latencyMs,
                                AiErrorCode errorCode) {
        AiUsageLog log = new AiUsageLog();
        log.guestSessionId = guestSessionId;
        log.requestId = requestId;
        log.callType = callType;
        log.model = model;
        log.promptVersion = promptVersion;
        log.inputTokens = inputTokens;
        log.outputTokens = outputTokens;
        log.latencyMs = latencyMs;
        log.result = errorCode == null ? AiCallResult.SUCCESS : AiCallResult.FAILED;
        log.errorCode = errorCode;
        log.createdAt = KoreaTime.now();
        return log;
    }
}
