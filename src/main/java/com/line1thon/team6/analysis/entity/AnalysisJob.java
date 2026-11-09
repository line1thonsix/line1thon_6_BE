package com.line1thon.team6.analysis.entity;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.global.common.KoreaTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 비교 분석 / 기초 설명 작업과 결과. 채팅당 평생 1개 (재시도는 같은 행을 다시 PENDING으로).
 * 컬럼은 docs/db-schema.md의 analysis_job 테이블을 따른다.
 * <p>
 * 생성(PENDING)과 완료(DONE)는 잠금 상태와 함께 바뀌어야 해서 A의 LockService가 한다.
 * 결과 저장(findings, basicExplanation), 실패, 재시도는 B가 이 엔티티에 메서드를 추가한다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_job_chat", columnNames = "chat_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisJob {

    /** 첫 시도 포함 최대 시도 횟수 */
    public static final int MAX_ATTEMPTS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 채팅을 지우면 DB의 FK(ON DELETE CASCADE)가 작업도 지운다 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, foreignKey = @ForeignKey(name = "fk_job_chat"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Chat chat;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private AnalysisMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private AnalysisStatus status;

    /** 첫 시도 포함 시도 횟수 1~3. retryCount = attemptCount - 1, remainingRetries = 3 - attemptCount */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int attemptCount;

    /** AI_TIMEOUT / AI_INVALID_JSON / AI_API_ERROR */
    @Column(length = 30)
    private String errorCode;

    /** COMPARE 결과 항목 배열. [{category, title, detail, userQuote, followUpQuestion}] */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Map<String, Object>> findings;

    /** BASIC일 때만 */
    @Column(columnDefinition = "TEXT")
    private String basicExplanation;

    /** 저장된 예시 응답인지 */
    @Column(nullable = false)
    private boolean isDemo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** DONE이 된 시각 */
    private LocalDateTime completedAt;

    /** 작업 시작 (전이 T4, T5): PENDING, 첫 시도 */
    public static AnalysisJob start(Chat chat, AnalysisMode mode) {
        AnalysisJob job = new AnalysisJob();
        job.chat = chat;
        job.mode = mode;
        job.status = AnalysisStatus.PENDING;
        job.attemptCount = 1;
        job.isDemo = false;
        job.createdAt = KoreaTime.now();
        return job;
    }

    /** 작업 완료 (전이 T6). LockService.completeJob에서만 부른다. 잠금 해제와 같은 트랜잭션이어야 한다 */
    public void complete() {
        if (status != AnalysisStatus.PENDING) {
            throw new IllegalStateException("PENDING인 작업만 완료할 수 있습니다: jobId=" + id + " status=" + status);
        }
        this.status = AnalysisStatus.DONE;
        this.completedAt = KoreaTime.now();
    }

    public int retryCount() {
        return attemptCount - 1;
    }

    public int remainingRetries() {
        return MAX_ATTEMPTS - attemptCount;
    }
}
