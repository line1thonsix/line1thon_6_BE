package com.line1thon.team6.session.repository;

import com.line1thon.team6.session.entity.GuestSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GuestSessionRepository extends JpaRepository<GuestSession, Long> {

    Optional<GuestSession> findByToken(String token);
}
