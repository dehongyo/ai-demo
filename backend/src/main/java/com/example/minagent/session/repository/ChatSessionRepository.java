package com.example.minagent.session.repository;

import com.example.minagent.session.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    @Query("select s from ChatSession s where s.user.id = :userId order by s.updatedAt desc")
    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Query("select s from ChatSession s join fetch s.user where s.id = :id")
    Optional<ChatSession> findByIdWithUser(UUID id);
}
