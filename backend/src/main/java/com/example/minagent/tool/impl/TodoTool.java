package com.example.minagent.tool.impl;

import com.example.minagent.session.SessionService;
import com.example.minagent.session.TodoItem;
import com.example.minagent.todo.TodoItemRepository;
import com.example.minagent.tool.AgentTool;
import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolDefinition;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class TodoTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TodoItemRepository todoRepository;
    private final SessionService sessionService;

    public TodoTool(TodoItemRepository todoRepository, SessionService sessionService) {
        this.todoRepository = todoRepository;
        this.sessionService = sessionService;
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = MAPPER.createObjectNode();

        ObjectNode actionProp = MAPPER.createObjectNode();
        actionProp.put("type", "string");
        var actionEnum = actionProp.putArray("enum");
        actionEnum.add("create");
        actionEnum.add("list");
        actionEnum.add("complete");
        props.set("action", actionProp);

        ObjectNode contentProp = MAPPER.createObjectNode();
        contentProp.put("type", "string");
        contentProp.put("minLength", 1);
        contentProp.put("maxLength", 500);
        props.set("content", contentProp);

        ObjectNode todoIdProp = MAPPER.createObjectNode();
        todoIdProp.put("type", "string");
        todoIdProp.put("format", "uuid");
        props.set("todoId", todoIdProp);

        schema.set("properties", props);
        var required = schema.putArray("required");
        required.add("action");
        schema.put("additionalProperties", false);

        return ToolDefinition.of("todo",
                "在当前会话中创建、查看或完成待办。用户要求记住、记录、列出或完成任务时使用。",
                schema);
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        // Verify session ownership
        sessionService.requireOwnedSession(context.userId(), context.sessionId());

        String action = arguments.path("action").asText();

        return switch (action) {
            case "create" -> handleCreate(arguments, context);
            case "list" -> handleList(context);
            case "complete" -> handleComplete(arguments, context);
            default -> ToolResult.failure("INVALID_ARGUMENTS",
                    "未知操作: " + action + "，支持: create, list, complete");
        };
    }

    private ToolResult handleCreate(JsonNode arguments, ToolContext context) {
        String content = arguments.path("content").asText();
        if (content == null || content.isBlank()) {
            return ToolResult.failure("INVALID_ARGUMENTS",
                    "create 操作必须提供 content 字段");
        }

        TodoItem item = todoRepository.create(context.userId(), context.sessionId(), content);

        ObjectNode data = MAPPER.createObjectNode();
        data.put("todoId", item.id().toString());
        data.put("content", item.content());
        data.put("status", item.status());

        return ToolResult.success(data,
                "已创建待办: " + item.content() + " (ID: " + item.id() + ")");
    }

    private ToolResult handleList(ToolContext context) {
        List<TodoItem> items = todoRepository.findByUserAndSession(
                context.userId(), context.sessionId());

        ArrayNode data = MAPPER.createArrayNode();
        StringBuilder sb = new StringBuilder("当前会话待办列表:\n");

        if (items.isEmpty()) {
            sb.append("(空) 暂无待办事项。");
        } else {
            for (int i = 0; i < items.size(); i++) {
                TodoItem item = items.get(i);
                ObjectNode node = MAPPER.createObjectNode();
                node.put("todoId", item.id().toString());
                node.put("content", item.content());
                node.put("status", item.status());
                node.put("createdAt", item.createdAt().toString());
                data.add(node);

                String statusMark = item.isOpen() ? "○" : "✓";
                sb.append(i + 1).append(". ").append(statusMark).append(" ")
                        .append(item.content()).append("\n");
            }
        }

        return ToolResult.success(data, sb.toString());
    }

    private ToolResult handleComplete(JsonNode arguments, ToolContext context) {
        String todoIdStr = arguments.path("todoId").asText();
        if (todoIdStr == null || todoIdStr.isBlank()) {
            return ToolResult.failure("INVALID_ARGUMENTS",
                    "complete 操作必须提供 todoId 字段");
        }

        UUID todoId;
        try {
            todoId = UUID.fromString(todoIdStr);
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("INVALID_ARGUMENTS",
                    "todoId 不是有效的 UUID: " + todoIdStr);
        }

        todoRepository.complete(todoId, context.userId(), context.sessionId());

        ObjectNode data = MAPPER.createObjectNode();
        data.put("todoId", todoIdStr);
        data.put("status", "COMPLETED");

        return ToolResult.success(data,
                "已标记待办 " + todoIdStr + " 为完成。");
    }
}
