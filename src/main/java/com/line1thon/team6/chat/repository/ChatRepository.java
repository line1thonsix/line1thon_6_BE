package com.line1thon.team6.chat.repository;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** 같은 세션이 같은 전송 ID로 이미 만든 대화 (중복 전송 확인) */
    Optional<Chat> findByGuestSessionAndClientRequestId(GuestSession guestSession, String clientRequestId);

    /** 내 대화만 찾는다 (소유권 확인). 남의 대화도 없는 대화처럼 빈 값 */
    Optional<Chat> findByIdAndGuestSession(Long id, GuestSession guestSession);

    /**
     * FAILED일 때만 PREPARING으로 바꾼다 (선생성 재시도). 확인과 변경이 쿼리 하나라 중복 클릭에도 한 번만 바뀐다.
     * 벌크 쿼리는 @PreUpdate를 거치지 않아서 updated_at도 여기서 바꾼다.
     * @return 바뀐 행 수. 0이면 FAILED가 아니었음
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Chat c
               set c.prepareStatus = com.line1thon.team6.chat.entity.PrepareStatus.PREPARING,
                   c.prepareStartedAt = :now,
                   c.updatedAt = :now
             where c.id = :id
               and c.prepareStatus = com.line1thon.team6.chat.entity.PrepareStatus.FAILED
            """)
    int restartPrepareIfFailed(@Param("id") Long id, @Param("now") LocalDateTime now);
}
