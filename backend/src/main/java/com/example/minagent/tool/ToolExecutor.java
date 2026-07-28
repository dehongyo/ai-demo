package com.example.minagent.tool;

import com.example.minagent.agent.AgentDecision;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);
    private static final int MAX_RESULT_SIZE_CHARS = 32768;

    private final ToolRegistry registry;
    private final ToolSchemaValidator validator;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolExecutor(ToolRegistry registry, ToolSchemaValidator validator) {
        this.registry = registry;
        this.validator = validator;
    }

    public ToolResult execute(AgentDecision.RequestedToolCall call, ToolContext context) {
        long start = System.currentTimeMillis();
        String toolName = call.toolName();
        JsonNode arguments = call.arguments();

        try {
            AgentTool tool = registry.require(toolName);

            // Validate arguments
            Optional<String> validationError = validator.validate(tool.definition(), arguments);
            if (validationError.isPresent()) {
                long duration = System.currentTimeMillis() - start;
                log.warn("Tool {} validation failed ({}ms): {}", toolName, duration, validationError.get());
                return ToolResult.failure("INVALID_ARGUMENTS",
                        "Tool " + toolName + " argument error: " + validationError.get() +
                                ". Schema requires: " + tool.definition().parametersSchema());
            }

            // Execute tool
            ToolResult result = tool.execute(arguments, context);
            long duration = System.currentTimeMillis() - start;
            log.info("Tool {} executed ({}ms): success={}", toolName, duration, result.success());

            // Truncate large results
            if (result.modelMessage() != null && result.modelMessage().length() > MAX_RESULT_SIZE_CHARS) {
                log.warn("Tool {} result truncated (size: {})", toolName, result.modelMessage().length());
                return new ToolResult(result.success(), result.code(), result.data(),
                        result.modelMessage().substring(0, MAX_RESULT_SIZE_CHARS)
                                + "... [truncated]");
            }

            return result;
        } catch (UnknownToolException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("Unknown tool requested: {} ({}ms)", toolName, duration);
            return ToolResult.failure("UNKNOWN_TOOL",
                    "Unknown tool: " + toolName + ". Available tools: " + registry.getToolNames());
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Tool {} execution error ({}ms): {}", toolName, duration, e.getMessage(), e);
            return ToolResult.failure("TOOL_ERROR",
                    "Tool " + toolName + " encountered an internal error: " + e.getMessage());
        }
    }
}
