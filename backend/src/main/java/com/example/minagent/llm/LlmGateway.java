package com.example.minagent.llm;

import com.example.minagent.llm.dto.ChatCompletionResponse;
import com.example.minagent.llm.dto.LlmMessage;
import com.example.minagent.llm.dto.LlmToolDefinition;

import java.util.List;

public interface LlmGateway {
    ChatCompletionResponse chat(List<LlmMessage> messages, List<LlmToolDefinition> tools);
}
