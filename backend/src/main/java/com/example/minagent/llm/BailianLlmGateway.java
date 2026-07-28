package com.example.minagent.llm;

import com.example.minagent.config.BailianProperties;
import com.example.minagent.llm.dto.ChatCompletionRequest;
import com.example.minagent.llm.dto.ChatCompletionResponse;
import com.example.minagent.llm.dto.LlmMessage;
import com.example.minagent.llm.dto.LlmToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class BailianLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(BailianLlmGateway.class);

    private final WebClient webClient;
    private final BailianProperties properties;

    public BailianLlmGateway(BailianProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ChatCompletionResponse chat(List<LlmMessage> messages, List<LlmToolDefinition> tools) {
        ChatCompletionRequest request = ChatCompletionRequest.of(
                properties.getModel(), messages, tools);

        int attempts = 0;
        int maxRetries = properties.getMaxRetries();
        Exception lastException = null;

        while (attempts <= maxRetries) {
            try {
                return doChat(request);
            } catch (RateLimitedException e) {
                attempts++;
                log.warn("LLM rate limited (attempt {}/{}), waiting before retry...",
                        attempts, maxRetries + 1);
                if (attempts > maxRetries) {
                    throw new LlmServiceException("LLM service rate limited after " + attempts + " attempts", e);
                }
                sleepWithBackoff(attempts);
                lastException = e;
            } catch (AuthException e) {
                throw e; // don't retry auth errors
            } catch (LlmServiceException e) {
                if (isRetryable(e)) {
                    attempts++;
                    log.warn("LLM retryable error (attempt {}/{}): {}",
                            attempts, maxRetries + 1, e.getMessage());
                    if (attempts > maxRetries) {
                        throw e;
                    }
                    sleepWithBackoff(attempts);
                    lastException = e;
                } else {
                    throw e;
                }
            }
        }

        throw new LlmServiceException("LLM call failed after " + (maxRetries + 1) + " attempts", lastException);
    }

    private ChatCompletionResponse doChat(ChatCompletionRequest request) {
        try {
            return webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(status -> status.value() == 401 || status.value() == 403, response ->
                            Mono.error(new AuthException("LLM auth failed ("
                                    + response.statusCode() + "). Check API key, base URL, and region.")))
                    .onStatus(status -> status.value() == 429, response ->
                            Mono.error(new RateLimitedException("LLM rate limited (429)")))
                    .onStatus(status -> status.is5xxServerError(), response ->
                            Mono.error(new LlmServiceException("LLM server error (" + response.statusCode() + ")")))
                    .bodyToMono(ChatCompletionResponse.class)
                    .timeout(properties.getResponseTimeout())
                    .block();
        } catch (AuthException | RateLimitedException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmServiceException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private boolean isRetryable(LlmServiceException e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("408") || msg.contains("429") || msg.contains("502")
                || msg.contains("503") || msg.contains("504") || msg.contains("timeout");
    }

    private void sleepWithBackoff(int attempt) {
        try {
            long ms = (long) (Math.pow(2, attempt) * 500 + Math.random() * 200);
            Thread.sleep(Math.min(ms, 5000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
