package com.example.minagent.session.repository;

import com.example.minagent.session.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query("select m from ChatMessage m where m.session.id = :sessionId order by m.sequenceNo asc")
    List<ChatMessage> findBySessionIdOrderBySequenceNoAsc(UUID sessionId);

    @Query("select m from ChatMessage m where m.session.id = :sessionId order by m.sequenceNo desc limit :count")
    List<ChatMessage> findRecentBySessionId(UUID sessionId, int count);

    @Query("select count(m) from ChatMessage m where m.session.id = :sessionId")
    long countBySessionId(UUID sessionId);

    @Query("select coalesce(max(m.sequenceNo), 0) from ChatMessage m where m.session.id = :sessionId")
    long maxSequenceNoBySessionId(UUID sessionId);
}
