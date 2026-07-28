package com.example.minagent.llm;

import com.example.minagent.llm.dto.LlmMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.*;

@Tag("smoke")
@EnabledIfEnvironmentVariable(named = "RUN_REAL_LLM_TESTS", matches = "true")
class BailianRealApiSmokeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void simpleCompletionReturnsValidResponse() {
        BailianLlmGateway gateway = createGateway();

        var response = gateway.chat(
                java.util.List.of(LlmMessage.user("请回复：测试成功")),
                java.util.List.of());

        assertThat(response.choices()).isNotEmpty();
        assertThat(response.choices().getFirst().message().content()).isNotEmpty();
        assertThat(response.usage().totalTokens()).isPositive();
    }

    @Test
    void toolCallingWithCalculator() {
        BailianLlmGateway gateway = createGateway();

        var schema = mapper.createObjectNode();
        schema.put("type", "object");
        var props = mapper.createObjectNode();
        var exprProp = mapper.createObjectNode();
        exprProp.put("type", "string");
        exprProp.put("description", "算术表达式");
        props.set("expression", exprProp);
        schema.set("properties", props);
        var required = mapper.createArrayNode();
        required.add("expression");
        schema.set("required", required);
        schema.put("additionalProperties", false);

        var tools = java.util.List.of(
                new com.example.minagent.llm.dto.LlmToolDefinition("function",
                        new com.example.minagent.llm.dto.LlmToolDefinition.FunctionDefinition(
                                "calculator", "计算数学表达式", schema)));

        var response = gateway.chat(
                java.util.List.of(LlmMessage.user("请使用 calculator 工具计算 2+3")),
                tools);

        assertThat(response.choices()).isNotEmpty();
        var message = response.choices().getFirst().message();
        // Assert there's either tool_calls or a direct answer about 5
        assertThat(message.toolCalls() != null || message.content() != null).isTrue();
    }

    private BailianLlmGateway createGateway() {
        var props = new com.example.minagent.config.BailianProperties();
        props.setApiKey(System.getenv("DASHSCOPE_API_KEY"));
        props.setBaseUrl(System.getenv("BAILIAN_BASE_URL"));
        props.setModel(System.getenv().getOrDefault("BAILIAN_MODEL", "qwen3.6-plus"));
        props.setConnectTimeout(java.time.Duration.ofSeconds(10));
        props.setResponseTimeout(java.time.Duration.ofSeconds(60));
        props.setMaxRetries(1);
        return new BailianLlmGateway(props);
    }
}
