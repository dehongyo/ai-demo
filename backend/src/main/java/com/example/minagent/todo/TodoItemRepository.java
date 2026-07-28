package com.example.minagent.todo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TodoItemRepository {

    private final JdbcTemplate jdbc;

    private static final RowMapper<com.example.minagent.session.TodoItem> ROW_MAPPER = new RowMapper<>() {
        @Override
        public com.example.minagent.session.TodoItem mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp completedTs = rs.getTimestamp("completed_at");
            return new com.example.minagent.session.TodoItem(
                    rs.getObject("id", UUID.class),
                    rs.getObject("user_id", UUID.class),
                    rs.getObject("session_id", UUID.class),
                    rs.getString("content"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    completedTs != null ? completedTs.toInstant() : null
            );
        }
    };

    public TodoItemRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public com.example.minagent.session.TodoItem create(UUID userId, UUID sessionId, String content) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into todo_item (id, user_id, session_id, content, status) values (?, ?, ?, ?, 'OPEN')",
                id, userId, sessionId, content
        );
        return findById(id).orElseThrow();
    }

    public Optional<com.example.minagent.session.TodoItem> findById(UUID id) {
        List<com.example.minagent.session.TodoItem> results = jdbc.query(
                "select * from todo_item where id = ?", ROW_MAPPER, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<com.example.minagent.session.TodoItem> findByUserAndSession(UUID userId, UUID sessionId) {
        return jdbc.query(
                "select * from todo_item where user_id = ? and session_id = ? order by created_at desc",
                ROW_MAPPER, userId, sessionId
        );
    }

    public void complete(UUID id, UUID userId, UUID sessionId) {
        jdbc.update(
                "update todo_item set status = 'COMPLETED', completed_at = ? where id = ? and user_id = ? and session_id = ?",
                Timestamp.from(Instant.now()), id, userId, sessionId
        );
    }
}
