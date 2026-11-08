package com.line1thon.team6.chat.entity;

import com.line1thon.team6.global.common.KoreaTime;
import com.line1thon.team6.session.entity.GuestSession;
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

/**
 * 채팅 하나 (질문, 잠금 상태, 선생성 결과). 컬럼은 docs/db-schema.md의 chat 테이블을 따른다.
 * 상태값은 setter 없이 의미 있는 메서드로만 바꾼다.
 * enum은 DB enum 타입이 아니라 VARCHAR로 저장한다 (DB 스키마 문서 규칙. Hibernate 기본값은 enum 타입).
 */
@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_request",
                columnNames = {"guest_session_id", "client_request_id"}),
        indexes = @Index(name = "idx_chat_guest_created", columnList = "guest_session_id, created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 소유자. 세션을 지우면 DB의 FK(ON DELETE CASCADE)가 채팅도 지운다 (JPA cascade는 쓰지 않음) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guest_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_guest"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private GuestSession guestSession;

    /** 중복 전송 방지용 전송 ID (UUID) */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 36)
    private String clientRequestId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private QuestionType type;

    /** 예시 질문 카드로 보낸 경우 */
    @Column(length = 40)
    private String exampleId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String question;

    /** 비밀값은 [REDACTED]로 바꿔 저장 */
    @Column(columnDefinition = "TEXT")
    private String code;

    @Column(nullable = false)
    private boolean secretMasked;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ChatStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PrepareStatus prepareStatus;

    /** 선생성 시작(재시도 포함) 시각. 1분 넘게 PREPARING이면 FAILED 처리할 때 사용 */
    @Column(nullable = false)
    private LocalDateTime prepareStartedAt;

    /** 공개된 힌트 단계 0~2 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int hintLevel;

    /** 선택지 칩 3개. 선생성이 끝나면 채움 */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> choices;

    /** 예시 문장. 선생성이 끝나면 채움 */
    @Column(length = 500)
    private String exampleSentence;

    /** 힌트 2단계. 선생성이 끝나면 채움 */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> hints;

    /** 기준 답변. 잠금이 풀린 뒤에만 응답에 넣는다 */
    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 질문 생성 (전이 T1): LOCKED, 힌트 0단계, 선생성 대기 */
    public static Chat create(GuestSession guestSession, String clientRequestId, QuestionType type,
                              String exampleId, String question, String code, boolean secretMasked) {
        Chat chat = new Chat();
        LocalDateTime now = KoreaTime.now();
        chat.guestSession = guestSession;
        chat.clientRequestId = clientRequestId;
        chat.type = type;
        chat.exampleId = exampleId;
        chat.question = question;
        chat.code = code;
        chat.secretMasked = secretMasked;
        chat.status = ChatStatus.LOCKED;
        chat.prepareStatus = PrepareStatus.PREPARING;
        chat.prepareStartedAt = now;
        chat.hintLevel = 0;
        chat.createdAt = now;
        chat.updatedAt = now;
        return chat;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = KoreaTime.now();
    }
}
