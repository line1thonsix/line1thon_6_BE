package com.line1thon.team6.chat.repository;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Long> {

    /** 같은 세션이 같은 전송 ID로 이미 만든 대화 (중복 전송 확인) */
    Optional<Chat> findByGuestSessionAndClientRequestId(GuestSession guestSession, String clientRequestId);
}
