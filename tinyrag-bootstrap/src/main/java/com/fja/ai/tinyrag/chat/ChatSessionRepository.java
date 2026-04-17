package com.fja.ai.tinyrag.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findAllByOrderByUpdatedAtDesc();
}
