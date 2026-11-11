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

    /**
     * 지금 시도를 시작한 시각. 첫 시도는 생성 시각이고 재시도하면 다시 기록한다.
     * 오래 PENDING인 작업(서버 재시작 등으로 사라진 작업)을 실패로 바꾸는 기준이다.
     * 이 컬럼이 생기기 전에 만든 행은 null이라 createdAt을 쓴다.
     */
    private LocalDateTime startedAt;

    /** 작업 시작 (전이 T4, T5): PENDING, 첫 시도 */
    public static AnalysisJob start(Chat chat, AnalysisMode mode) {
        AnalysisJob job = new AnalysisJob();
        job.chat = chat;
        job.mode = mode;
        job.status = AnalysisStatus.PENDING;
        job.attemptCount = 1;
        job.isDemo = false;
        job.createdAt = KoreaTime.now();
        job.startedAt = job.createdAt;
        return job;
    }

    /** 재시도 가능 횟수가 남았는지 (첫 시도 포함 최대 MAX_ATTEMPTS번) */
    public boolean canRetry() {
        return attemptCount < MAX_ATTEMPTS;
    }

    /**
     * 재시도 (B). FAILED → PENDING, 시도 횟수 + 1. LockService.retryJob에서 채팅 행을 잠근 뒤에만 부른다.
     * 같은 행을 다시 쓰고, 생각(최초 작성본)은 그대로 쓴다.
     */
    public void retry() {
        if (status != AnalysisStatus.FAILED) {
            throw new IllegalStateException("FAILED인 작업만 재시도할 수 있습니다: jobId=" + id + " status=" + status);
        }
        if (!canRetry()) {
            throw new IllegalStateException("재시도 횟수를 모두 썼습니다: jobId=" + id + " attemptCount=" + attemptCount);
        }
        this.attemptCount++;
        this.status = AnalysisStatus.PENDING;
        this.errorCode = null;
        this.startedAt = KoreaTime.now();
    }

    /** PENDING이고 지금 시도를 threshold보다 먼저 시작했으면 true (오래 멈춘 작업) */
    public boolean isStale(LocalDateTime threshold) {
        LocalDateTime started = startedAt != null ? startedAt : createdAt;
        return status == AnalysisStatus.PENDING && started.isBefore(threshold);
    }

    /** 작업 완료 (전이 T6). LockService.completeJob에서만 부른다. 잠금 해제와 같은 트랜잭션이어야 한다 */
    public void complete() {
        if (status != AnalysisStatus.PENDING) {
            throw new IllegalStateException("PENDING인 작업만 완료할 수 있습니다: jobId=" + id + " status=" + status);
        }
        this.status = AnalysisStatus.DONE;
        this.completedAt = KoreaTime.now();
    }

    /**
     * 비교 분석 결과 저장 (B). 같은 트랜잭션에서 이어서 LockService.completeJob을 불러 DONE과 잠금 해제를 한다.
     * 재시도에 성공하면 이전 실패의 오류 코드를 지운다.
     */
    public void recordFindings(List<Map<String, Object>> findings) {
        requirePending("결과 저장");
        this.findings = findings;
        this.errorCode = null;
    }

    /**
     * 기초 설명 저장 (B). 같은 트랜잭션에서 이어서 LockService.completeJob을 불러 DONE과 잠금 해제(UNLOCKED_ASSISTED)를 한다.
     *
     * @param explanationJson {summary, exampleCode, observe, change} JSON 문자열
     */
    public void recordBasicExplanation(String explanationJson) {
        requirePending("결과 저장");
        this.basicExplanation = explanationJson;
        this.errorCode = null;
    }

    /** AI 호출 실패 (B). 잠금은 그대로(LOCKED) 두고, 사용자는 재시도할 수 있다 */
    public void fail(String errorCode) {
        requirePending("실패 처리");
        this.status = AnalysisStatus.FAILED;
        this.errorCode = errorCode;
    }

    private void requirePending(String action) {
        if (status != AnalysisStatus.PENDING) {
            throw new IllegalStateException("PENDING인 작업만 " + action + "할 수 있습니다: jobId=" + id + " status=" + status);
        }
    }

    public int retryCount() {
        return attemptCount - 1;
    }

    public int remainingRetries() {
        return MAX_ATTEMPTS - attemptCount;
    }
}
