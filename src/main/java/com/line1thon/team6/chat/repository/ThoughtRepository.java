package com.line1thon.team6.chat.repository;

import com.line1thon.team6.chat.entity.Chat;
import com.line1thon.team6.chat.entity.Thought;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThoughtRepository extends JpaRepository<Thought, Long> {

    Optional<Thought> findByChat(Chat chat);
}
