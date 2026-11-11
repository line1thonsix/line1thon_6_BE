package com.line1thon.team6.session.repository;

import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GuestSessionRepository extends JpaRepository<GuestSession, Long> {

    Optional<GuestSession> findByToken(String token);

    /**
     * 세션을 지운다. 채팅은 FK(ON DELETE CASCADE)로, 채팅에 딸린 생각·분석 작업은 다시 채팅의 FK로 함께 지워진다.
     * AI 사용 기록(ai_usage_log)은 FK가 없어서 남는다 (docs/db-schema.md).
     * @return 지운 행 수
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from GuestSession g where g.id = :id")
    int deleteSession(@Param("id") Long id);
}
