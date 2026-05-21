package com.fja.ai.tinyrag.chat;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findAllByOrderByUpdatedAtDesc();

    @Query("SELECT s.kb as kb, COUNT(s) as cnt FROM ChatSession s GROUP BY s.kb ORDER BY cnt DESC")
    List<Object[]> countByKb();
}
