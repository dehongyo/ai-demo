package com.example.minagent.session.repository;

import com.example.minagent.session.SessionSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SessionSummaryRepository extends JpaRepository<SessionSummary, UUID> {

    @Query("select s from SessionSummary s where s.session.id = :sessionId")
    Optional<SessionSummary> findBySessionId(UUID sessionId);
}
