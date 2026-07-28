package com.example.minagent.llm;

import com.example.minagent.config.BailianProperties;
import com.example.minagent.llm.dto.ChatCompletionResponse;
import com.example.minagent.llm.dto.LlmMessage;
import com.example.minagent.llm.dto.LlmToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class BailianLlmGatewayTest {

    private MockWebServer mockServer;
    private BailianLlmGateway gateway;
    private BailianProperties properties;
    private ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        properties = new BailianProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("http://localhost:" + mockServer.getPort());
        properties.setModel("test-model");
        properties.setConnectTimeout(Duration.ofSeconds(5));
        properties.setResponseTimeout(Duration.ofSeconds(10));
        properties.setMaxRetries(1);

        gateway = new BailianLlmGateway(properties);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void sendsCorrectAuthorizationAndModel() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":"chat-1","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """));

        gateway.chat(List.of(LlmMessage.user("hi")), List.of());

        RecordedRequest req = mockServer.takeRequest();
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(req.getHeader("Content-Type")).contains("application/json");
        assertThat(req.getBody().readUtf8()).contains("\"model\":\"test-model\"");
    }

    @Test
    void sendsToolChoiceAutoByDefault() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":"chat-2","choices":[{"index":0,"message":{"role":"assistant","content":"Hello"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """));

        gateway.chat(List.of(LlmMessage.user("hi")), List.of());

        RecordedRequest req = mockServer.takeRequest();
        assertThat(req.getBody().readUtf8()).contains("\"tool_choice\":\"auto\"");
    }

    @Test
    void parsesValidResponse() {
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":"chat-3","choices":[{"index":0,"message":{"role":"assistant","content":"Hello there"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """));

        ChatCompletionResponse response = gateway.chat(List.of(LlmMessage.user("hi")), List.of());

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().getFirst().message().content()).isEqualTo("Hello there");
        assertThat(response.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void doesNotRetryOn401() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(401));

        assertThatThrownBy(() -> gateway.chat(List.of(LlmMessage.user("hi")), List.of()))
                .isInstanceOf(AuthException.class);

        assertThat(mockServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void doesNotRetryOn403() throws Exception {
        mockServer.enqueue(new MockResponse().setResponseCode(403));

        assertThatThrownBy(() -> gateway.chat(List.of(LlmMessage.user("hi")), List.of()))
                .isInstanceOf(AuthException.class);

        assertThat(mockServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void retriesOn429ThenSucceeds() {
        mockServer.enqueue(new MockResponse().setResponseCode(429));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":"chat-4","choices":[{"index":0,"message":{"role":"assistant","content":"success"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """));

        ChatCompletionResponse response = gateway.chat(List.of(LlmMessage.user("hi")), List.of());

        assertThat(response.choices().getFirst().message().content()).isEqualTo("success");
    }

    @Test
    void retriesOn503ThenSucceeds() {
        mockServer.enqueue(new MockResponse().setResponseCode(503));
        mockServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"id":"chat-5","choices":[{"index":0,"message":{"role":"assistant","content":"success"},"finish_reason":"stop"}],"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                    """));

        ChatCompletionResponse response = gateway.chat(List.of(LlmMessage.user("hi")), List.of());

        assertThat(response.choices().getFirst().message().content()).isEqualTo("success");
    }

    @Test
    void failsAfterMaxRetries() {
        // maxRetries=1 means 2 total attempts, after that fails
        mockServer.enqueue(new MockResponse().setResponseCode(429));
        mockServer.enqueue(new MockResponse().setResponseCode(429));

        assertThatThrownBy(() -> gateway.chat(List.of(LlmMessage.user("hi")), List.of()))
                .isInstanceOf(LlmServiceException.class);

        // 1 initial + 1 retry = 2 attempts
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
    }
}
