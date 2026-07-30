package com.example.minagent.agent;

import com.example.minagent.config.AgentProperties;
import com.example.minagent.llm.LlmGateway;
import com.example.minagent.llm.dto.*;
import com.example.minagent.session.*;
import com.example.minagent.session.repository.*;
import com.example.minagent.tool.ToolExecutor;
import com.example.minagent.tool.ToolRegistry;
import com.example.minagent.tool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@ActiveProfiles("test")
class AgentRuntimeTest {

    private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private ChatSessionRepository sessionRepository;
    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private SessionSummaryRepository summaryRepository;

    private LlmGateway llmGateway;
    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private SessionService sessionService;
    private AgentRuntime runtime;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        llmGateway = mock(LlmGateway.class);
        toolRegistry = mock(ToolRegistry.class);
        toolExecutor = mock(ToolExecutor.class);

        sessionService = new SessionService(appUserRepository, sessionRepository, messageRepository, summaryRepository);
        session = sessionService.createSession(USER_A, "Test Session");

        var parser = new LlmOutputParser();
        var lock = new SessionRunLock();
        var props = new AgentProperties();
        props.setMaxSteps(4);

        runtime = new AgentRuntime(sessionService, llmGateway, parser,
                toolRegistry, toolExecutor, lock, props);
    }

    private ChatCompletionResponse textResponse(String content) {
        return new ChatCompletionResponse("test-id",
                List.of(new ChatCompletionResponse.Choice(0,
                        new ChatCompletionResponse.AssistantMessage("assistant", content, null, null),
                        "stop")),
                new ChatCompletionResponse.Usage(10, 5, 15));
    }

    private ChatCompletionResponse toolCallResponse(String toolName, String arguments) {
        return new ChatCompletionResponse("test-id",
                List.of(new ChatCompletionResponse.Choice(0,
                        new ChatCompletionResponse.AssistantMessage("assistant", "",
                                null,
                                List.of(new LlmToolCall("call_1", "function",
                                        new LlmToolCall.LlmFunctionCall(toolName, arguments)))),
                        "tool_calls")),
                new ChatCompletionResponse.Usage(10, 5, 15));
    }

    @Test
    void directAnswerReturnsFinalResponse() {
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(textResponse("你好！我是 Minimal Agent，可以帮你完成任务。"));

        AgentRunResult result = runtime.run(new AgentRunCommand(USER_A, session.getId(), "你好"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.answer()).contains("你好");

        verify(llmGateway, times(1)).chat(anyList(), anyList());
    }

    @Test
    void toolCallThenFinalAnswer() {
        when(toolRegistry.definitions()).thenReturn(List.of());
        when(toolExecutor.execute(any(), any()))
                .thenReturn(ToolResult.success(null, "天气: 杭州小雨，27°C"));

        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"杭州\"}"))
                .thenReturn(textResponse("杭州当前小雨，温度27°C。"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "杭州天气如何"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.answer()).contains("杭州");
        verify(llmGateway, times(2)).chat(anyList(), anyList());
        verify(toolExecutor, times(1)).execute(any(), any());
    }

    @Test
    void invalidOutputRepairThenFinalAnswer() {
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(new ChatCompletionResponse("test-id",
                        List.of(new ChatCompletionResponse.Choice(0,
                                new ChatCompletionResponse.AssistantMessage("assistant", null, null, null),
                                "stop")),
                        new ChatCompletionResponse.Usage(10, 5, 15)))
                .thenReturn(textResponse("抱歉，让我重新回答。你好！"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "你好"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(llmGateway, times(2)).chat(anyList(), anyList());
    }

    @Test
    void stopsAfterMaxSteps() {
        // 每步返回不同的工具参数，避免触发回环检测
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"杭州\"}"))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"北京\"}"))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"上海\"}"))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"深圳\"}"));
        when(toolExecutor.execute(any(), any()))
                .thenReturn(ToolResult.success(null, "weather result"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "杭州天气"));

        assertThat(result.status()).isEqualTo("MAX_STEPS");
        // Verify called exactly maxSteps times (4)
        verify(llmGateway, times(4)).chat(anyList(), anyList());
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        UUID sessionOfOther = UUID.randomUUID();

        assertThatThrownBy(() -> runtime.run(new AgentRunCommand(
                USER_A, sessionOfOther, "hello")))
                .isInstanceOf(SessionNotFoundException.class);

        verifyNoInteractions(llmGateway);
    }

    @Test
    void terminatesWhenAllToolsFailUnrecoverable() {
        // LLM 返回 tool call
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"杭州\"}"));
        // 工具返回不可恢复的 TOOL_ERROR
        when(toolExecutor.execute(any(), any()))
                .thenReturn(ToolResult.failure("TOOL_ERROR", "内部服务错误"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "杭州天气如何"));

        // 不可恢复的失败 → Runtime 直接终止，不再调 LLM 进行第二轮
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.answer()).contains("失败");
        // 只调了一次 LLM（没有第二轮）
        verify(llmGateway, times(1)).chat(anyList(), anyList());
    }

    @Test
    void continuesWhenFailureIsRecoverable() {
        // LLM 第一步调了一个不存在的工具
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(toolCallResponse("unknown_tool", "{}"))
                .thenReturn(textResponse("对不起，我没有找到合适的工具"));
        when(toolExecutor.execute(any(), any()))
                .thenReturn(ToolResult.failure("UNKNOWN_TOOL", "未知工具: unknown_tool"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "帮我做一件事"));

        // 可恢复的失败（UNKNOWN_TOOL）→ CONTINUE，LLM 在第二步有机会修正
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(llmGateway, times(2)).chat(anyList(), anyList());
    }

    @Test
    void detectsToolLoopAndTerminates() {
        // LLM 每步都返回完全相同的工具调用
        when(llmGateway.chat(anyList(), anyList()))
                .thenReturn(toolCallResponse("weather", "{\"city\":\"杭州\"}"));
        when(toolExecutor.execute(any(), any()))
                .thenReturn(ToolResult.success(null, "杭州小雨"));

        AgentRunResult result = runtime.run(new AgentRunCommand(
                USER_A, session.getId(), "杭州天气如何"));

        // 回环检测触发（连续 3 次相同工具+参数），返回 ERROR
        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.answer()).contains("重复调用");
    }
}
