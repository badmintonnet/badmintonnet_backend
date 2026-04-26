package com.tlcn.sportsnet_backend.repository;

import com.tlcn.sportsnet_backend.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, String> {
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    @Query("SELECT m.sessionId FROM ChatMessage m WHERE m.account.id = ?1 GROUP BY m.sessionId ORDER BY MAX(m.createdAt) DESC")
    List<String> findDistinctSessionIdsByAccountIdOrderByLatestFirst(String accountId);

    @Query(
            value = "SELECT m.sessionId FROM ChatMessage m WHERE m.account.id = ?1 GROUP BY m.sessionId ORDER BY MAX(m.createdAt) DESC",
            countQuery = "SELECT COUNT(DISTINCT m.sessionId) FROM ChatMessage m WHERE m.account.id = ?1"
    )
    Page<String> findDistinctSessionIdsByAccountIdOrderByLatestFirst(String accountId, Pageable pageable);

    List<ChatMessage> findByAccountIdAndSessionIdOrderByCreatedAtAsc(String accountId, String sessionId);

    Page<ChatMessage> findByAccountIdAndSessionIdOrderByCreatedAtAsc(String accountId, String sessionId, Pageable pageable);

    Optional<ChatMessage> findFirstByAccountIdAndSessionIdOrderByCreatedAtDesc(String accountId, String sessionId);

    boolean existsByAccountIdAndSessionId(String accountId, String sessionId);

    List<ChatMessage> findByAccountIdOrderBySessionIdDescCreatedAtDesc(String accountId);

    Optional<ChatMessage> findFirstByAccountIdAndSessionIdAndRoleOrderByCreatedAtAsc(String accountId, String sessionId, String role);
    List<ChatMessage> findByChatSessionOrderByCreatedAtAsc(com.tlcn.sportsnet_backend.entity.ChatSession chatSession);
}
