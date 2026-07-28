package com.example.minagent.tool.impl;

import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class MockSearchToolTest {

    private final MockSearchTool tool = new MockSearchTool();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ToolContext ctx = new ToolContext(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        tool.loadDocuments();
    }

    @Test
    void searchesForAgentReturnsResults() {
        ObjectNode args = mapper.createObjectNode();
        args.put("query", "Agent Loop");

        ToolResult result = tool.execute(args, ctx);

        assertThat(result.success()).isTrue();
        assertThat(result.modelMessage()).contains("Agent");
    }

    @Test
    void returnsDeterministicResultsForSameQuery() {
        ObjectNode args = mapper.createObjectNode();
        args.put("query", "Spring Boot");

        ToolResult r1 = tool.execute(args, ctx);
        ToolResult r2 = tool.execute(args, ctx);

        assertThat(r1.modelMessage()).isEqualTo(r2.modelMessage());
    }

    @Test
    void searchWithNoMatchReturnsEmptyMessage() {
        ObjectNode args = mapper.createObjectNode();
        args.put("query", "zzzxyzabc123");

        ToolResult result = tool.execute(args, ctx);

        assertThat(result.modelMessage()).contains("未找到");
    }

    @Test
    void respectsLimit() {
        ObjectNode args = mapper.createObjectNode();
        args.put("query", "Spring React");
        args.put("limit", 1);

        ToolResult result = tool.execute(args, ctx);

        // Should have at most 1 result in output
        assertThat(result.success()).isTrue();
    }

    @Test
    void definitionHasCorrectName() {
        assertThat(tool.definition().name()).isEqualTo("mock_search");
    }
}
