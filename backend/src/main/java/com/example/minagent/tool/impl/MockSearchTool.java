package com.example.minagent.tool.impl;

import com.example.minagent.tool.AgentTool;
import com.example.minagent.tool.ToolContext;
import com.example.minagent.tool.ToolDefinition;
import com.example.minagent.tool.ToolResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class MockSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(MockSearchTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private List<SearchDocument> documents = List.of();

    @PostConstruct
    void loadDocuments() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("mock/search-documents.json")) {
            if (is != null) {
                documents = MAPPER.readValue(is,
                        new TypeReference<List<SearchDocument>>() {
                        });
                log.info("Loaded {} search documents", documents.size());
            } else {
                log.warn("search-documents.json not found, using empty document list");
            }
        } catch (Exception e) {
            log.error("Failed to load search documents", e);
        }
    }

    @Override
    public ToolDefinition definition() {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("type", "object");

        ObjectNode props = MAPPER.createObjectNode();
        ObjectNode queryProp = MAPPER.createObjectNode();
        queryProp.put("type", "string");
        queryProp.put("minLength", 1);
        queryProp.put("maxLength", 200);
        props.set("query", queryProp);

        ObjectNode limitProp = MAPPER.createObjectNode();
        limitProp.put("type", "integer");
        limitProp.put("minimum", 1);
        limitProp.put("maximum", 5);
        limitProp.put("default", 3);
        props.set("limit", limitProp);

        schema.set("properties", props);
        var required = schema.putArray("required");
        required.add("query");
        schema.put("additionalProperties", false);

        return ToolDefinition.of("mock_search",
                "从内置演示资料中搜索信息。查询项目、Java、Spring、React 或 Agent 概念时使用。",
                schema);
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String query = arguments.path("query").asText();
        int limit = arguments.has("limit") ? arguments.get("limit").asInt(3) : 3;
        limit = Math.max(1, Math.min(5, limit));

        List<String> queryWords = tokenize(query);

        List<SearchResult> results = new ArrayList<>();
        for (SearchDocument doc : documents) {
            int score = computeScore(doc, queryWords);
            if (score > 0) {
                results.add(new SearchResult(doc.title, doc.snippet, doc.source, score));
            }
        }

        results.sort(Comparator.comparingInt(SearchResult::score).reversed());
        List<SearchResult> top = results.subList(0, Math.min(limit, results.size()));

        ArrayNode data = MAPPER.createArrayNode();
        StringBuilder sb = new StringBuilder("搜索结果:\n");
        for (int i = 0; i < top.size(); i++) {
            SearchResult r = top.get(i);
            ObjectNode item = MAPPER.createObjectNode();
            item.put("title", r.title);
            item.put("snippet", r.snippet);
            item.put("source", r.source);
            item.put("score", r.score);
            data.add(item);

            sb.append(i + 1).append(". ").append(r.title)
                    .append(" (相关度: ").append(r.score).append(")\n")
                    .append("   ").append(r.snippet).append("\n");
        }

        if (top.isEmpty()) {
            sb.append("未找到相关结果，请尝试其他关键词。");
        }

        return ToolResult.success(data, sb.toString());
    }

    private List<String> tokenize(String text) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toLowerCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) || isCJK(c)) {
                current.append(c);
            } else {
                if (!current.isEmpty()) {
                    words.add(current.toString());
                    current = new StringBuilder();
                }
            }
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    private int computeScore(SearchDocument doc, List<String> queryWords) {
        int score = 0;
        for (String qw : queryWords) {
            // Check keywords
            for (String kw : doc.keywords) {
                if (kw.toLowerCase().contains(qw) || qw.contains(kw.toLowerCase())) {
                    score += 3;
                }
            }
            // Title match
            if (doc.title.toLowerCase().contains(qw)) {
                score += 2;
            }
            // Snippet match
            if (doc.snippet.toLowerCase().contains(qw)) {
                score += 1;
            }
        }
        return score;
    }

    private boolean isCJK(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    record SearchDocument(String title, String snippet, String source,
                          List<String> keywords) {
    }

    record SearchResult(String title, String snippet, String source,
                        int score) {
    }
}
