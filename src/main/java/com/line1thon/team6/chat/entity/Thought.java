package com.line1thon.team6.chat.entity;

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

/**
 * 생각 최초 작성본. 채팅당 1개이고 저장한 뒤에는 바꾸지 않는다 (setter 없음).
 * 컬럼은 docs/db-schema.md의 thought 테이블을 따른다.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_thought_chat", columnNames = "chat_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Thought {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 채팅을 지우면 DB의 FK(ON DELETE CASCADE)가 생각도 지운다 */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chat_id", nullable = false, foreignKey = @ForeignKey(name = "fk_thought_chat"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Chat chat;

    /** 앞뒤 공백을 뺀 30~2,000자 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 생각을 쓸 때 고른 칩 번호 0~2 (기록용) */
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Integer selectedChoice;

    /** 제출할 때 힌트를 하나라도 봤는지 (hintLevel > 0) */
    @Column(nullable = false)
    private boolean hintUsed;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static Thought create(Chat chat, String content, Integer selectedChoice) {
        Thought thought = new Thought();
        thought.chat = chat;
        thought.content = content;
        thought.selectedChoice = selectedChoice;
        thought.hintUsed = chat.getHintLevel() > 0;
        thought.createdAt = KoreaTime.now();
        return thought;
    }
}
