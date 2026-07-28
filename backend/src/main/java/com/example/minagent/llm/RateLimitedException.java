package com.example.minagent.llm;

public class RateLimitedException extends LlmServiceException {

    public RateLimitedException(String message) {
        super(message);
    }
}
