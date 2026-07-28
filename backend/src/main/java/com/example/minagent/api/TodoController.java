package com.example.minagent.api;

import com.example.minagent.session.SessionService;
import com.example.minagent.session.TodoItem;
import com.example.minagent.todo.TodoItemRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions/{sessionId}/todos")
public class TodoController {

    private final SessionService sessionService;
    private final TodoItemRepository todoRepository;

    public TodoController(SessionService sessionService, TodoItemRepository todoRepository) {
        this.sessionService = sessionService;
        this.todoRepository = todoRepository;
    }

    @GetMapping
    public List<TodoItem> listTodos(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID sessionId) {
        sessionService.requireOwnedSession(userId, sessionId);
        return todoRepository.findByUserAndSession(userId, sessionId);
    }
}
