package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.ChatSession;
import com.tlcn.sportsnet_backend.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByAccountOrderByUpdatedAtDesc(Account account);
    Optional<ChatSession> findBySessionId(String sessionId);
    Optional<ChatSession> findByAccountAndSessionId(Account account, String sessionId);
}

