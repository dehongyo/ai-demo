# Minimal Agent — 后端代码参考手册

## 1. 项目总览

```
backend/src/main/java/com/example/minagent/
├── MinimalAgentApplication.java      # Spring Boot 入口
├── config/
│   ├── AgentProperties.java          # Agent 行为配置（步数、压缩阈值等）
│   └── BailianProperties.java        # 百炼 LLM API 配置（Key、Base URL、超时）
├── agent/
│   ├── AgentDecision.java            # sealed interface：FINAL_ANSWER | TOOL_CALLS | INVALID
│   ├── AgentRuntime.java             # ★ 核心循环：感知→决策→行动→观察→终止
│   ├── AgentRunCommand.java          # 输入 DTO（userId, sessionId, content）
│   ├── AgentRunResult.java           # 输出 DTO（runId, messageId, answer, status）
│   ├── LlmOutputParser.java          # 解析百炼响应：content→FinalAnswer, tool_calls→ToolCall, 空→Invalid
│   ├── ParsedLlmOutput.java          # 统一输出结构（kind, summary, toolCalls, finalAnswer）
│   └── SessionRunLock.java           # Session 级并发锁（基于 ConcurrentHashMap + ReentrantLock）
├── llm/
│   ├── LlmGateway.java               # 接口：chat(messages, tools) → ChatCompletionResponse
│   ├── BailianLlmGateway.java        # ★ WebClient 调用百炼，指数退避重试
│   ├── LlmServiceException.java      # LLM 服务异常基类
│   ├── AuthException.java            # 401/403 认证异常（不重试）
│   ├── RateLimitedException.java     # 429 限流异常（重试）
│   └── dto/
│       ├── ChatCompletionRequest.java # 请求 DTO（model, messages, tools, tool_choice, temperature）
│       ├── ChatCompletionResponse.java# 响应 DTO（choices, usage）
│       ├── LlmMessage.java           # 消息 DTO（role, content, tool_calls, tool_call_id）
│       ├── LlmToolCall.java          # 工具调用 DTO（id, function.name, function.arguments）
│       └── LlmToolDefinition.java    # 工具定义 DTO（type, function: {name, description, parameters}）
├── tool/
│   ├── AgentTool.java                # 接口：definition() + execute(args, ctx)
│   ├── ToolDefinition.java           # 工具定义（name, description, JSON Schema）
│   ├── ToolContext.java              # 工具上下文（userId, sessionId, runId）—— 服务端注入
│   ├── ToolResult.java               # 工具结果（success, code, data, modelMessage）
│   ├── ToolRegistry.java             # ★ 自动发现所有 AgentTool Bean，去重，按名索引
│   ├── ToolSchemaValidator.java      # JSON Schema 校验：必填、类型、枚举、长度、范围
│   ├── ToolExecutor.java             # ★ 统一执行：查找工具→校验参数→执行→截断结果→计时
│   ├── UnknownToolException.java     # 未知工具异常
│   └── impl/
│       ├── ExpressionParser.java     # ★ 递归下降算术解析器（BigDecimal，支持 +-*/()，除零检测）
│       ├── CalculatorTool.java       # 计算器工具
│       ├── MockSearchTool.java       # 模拟搜索工具（固定 JSON 数据源，关键词匹配计分）
│       ├── WeatherTool.java          # 模拟天气工具（4 城市固定数据）
│       └── TodoTool.java             # 待办工具（create/list/complete，userId+sessionId 隔离）
├── memory/
│   ├── ContextAssembler.java         # 构建 LLM 上下文：System Prompt → Summary → 最近消息
│   ├── ContextCompressor.java        # ★ 压缩触发器：消息>30 或 token>12000 时触发
│   └── TokenEstimator.java           # 粗略 token 估算：CJK=1 token/字，非 CJK=0.25 token/字
├── session/
│   ├── AppUser.java                  # JPA 实体：用户
│   ├── ChatSession.java              # JPA 实体：会话（@Version 乐观锁）
│   ├── ChatMessage.java              # JPA 实体：消息（USER|ASSISTANT|ASSISTANT_TOOL_CALL|TOOL）
│   ├── SessionSummary.java           # JPA 实体：会话摘要
│   ├── TodoItem.java                 # record：待办项
│   ├── AgentRun.java                 # record：Agent 运行记录
│   ├── SessionService.java           # ★ Session 所有权校验 + 消息/摘要 CRUD
│   ├── SessionNotFoundException.java # 404 异常
│   ├── SessionBusyException.java     # 409 异常
│   └── repository/
│       ├── AppUserRepository.java    # JpaRepository<AppUser, UUID>
│       ├── ChatSessionRepository.java# 含自定义 @Query：按用户查会话、关联加载
│       ├── ChatMessageRepository.java# 含自定义 @Query：按会话查消息、最大序号
│       └── SessionSummaryRepository.java
├── todo/
│   └── TodoItemRepository.java       # JdbcTemplate 实现：create/findById/complete（userId+sessionId 强制过滤）
└── api/
    ├── SessionController.java        # Session CRUD：POST/GET 会话，GET 消息列表
    ├── AgentController.java          # ★ 发送消息（JSON + SSE 两种模式）
    ├── ToolController.java           # GET /api/tools：返回所有工具的 Schema
    ├── TodoController.java           # GET /api/sessions/{id}/todos：查询待办
    ├── ApiExceptionHandler.java      # ★ 统一异常处理：404/409/400/502/503/500
    └── dto/
        ├── CreateSessionRequest.java # @NotBlank title
        ├── SessionResponse.java      # id, title, createdAt, updatedAt
        ├── MessageResponse.java      # id, role, content, toolName, ...
        ├── SendMessageRequest.java   # @NotBlank content (max 8000)
        ├── AgentRunResponse.java     # runId, messageId, answer, status
        ├── ToolResponse.java         # name, description, parametersSchema
        └── ErrorResponse.java        # code, message, requestId, timestamp
```

---

## 2. 启动入口

### [MinimalAgentApplication.java](backend/src/main/java/com/example/minagent/MinimalAgentApplication.java)

```java
@SpringBootApplication
@EnableConfigurationProperties({BailianProperties.class, AgentProperties.class})
public class MinimalAgentApplication { ... }
```

**作用：** Spring Boot 标准入口 + 启用 `@ConfigurationProperties` 绑定。`@EnableConfigurationProperties` 把 `app.llm.*` 和 `app.agent.*` 配置项绑定到 `BailianProperties` 和 `AgentProperties` 两个 Bean。

---

## 3. 配置模块 (`config/`)

### [BailianProperties.java](backend/src/main/java/com/example/minagent/config/BailianProperties.java)

绑定 `application.yml` 中的 `app.llm.*`：

| 字段                | 配置键                       | 默认值                   | 说明                         |
| ------------------- | ---------------------------- | ------------------------ | ---------------------------- |
| `apiKey`          | `app.llm.api-key`          | `${DASHSCOPE_API_KEY}` | 百炼 API Key，从环境变量读取 |
| `baseUrl`         | `app.llm.base-url`         | `${BAILIAN_BASE_URL}`  | 百炼业务空间 Base URL        |
| `model`           | `app.llm.model`            | `qwen3.6-plus`         | 模型名                       |
| `connectTimeout`  | `app.llm.connect-timeout`  | `5s`                   | 连接超时                     |
| `responseTimeout` | `app.llm.response-timeout` | `60s`                  | 响应超时                     |
| `maxRetries`      | `app.llm.max-retries`      | `2`                    | 最大重试次数                 |

关键方法 `getChatCompletionsUrl()` 拼接 `{baseUrl}/chat/completions`。

### [AgentProperties.java](backend/src/main/java/com/example/minagent/config/AgentProperties.java)

绑定 `application.yml` 中的 `app.agent.*`：

| 字段                         | 配置键                                   | 默认值    | 说明                            |
| ---------------------------- | ---------------------------------------- | --------- | ------------------------------- |
| `maxSteps`                 | `app.agent.max-steps`                  | `8`     | 单次 Run 最大步数，防止无限循环 |
| `recentMessageCount`       | `app.agent.recent-message-count`       | `12`    | 放入 Context 的最近消息数       |
| `compressMessageThreshold` | `app.agent.compress-message-threshold` | `30`    | 触发压缩的消息数阈值            |
| `contextTokenBudget`       | `app.agent.context-token-budget`       | `12000` | 触发压缩的 token 估算阈值       |

---

## 4. Session 与数据持久化 (`session/`)

### 数据库表（[V1__init.sql](backend/src/main/resources/db/migration/V1__init.sql)）

8 张表，ER 关系如下：

```
app_user ──1:N──> chat_session ──1:N──> chat_message
                          │
                          ├──1:1──> session_summary
                          ├──1:N──> todo_item
                          └──1:N──> agent_run ──1:N──> agent_step ──1:N──> tool_execution
```

### [AppUser.java](backend/src/main/java/com/example/minagent/session/AppUser.java)

最简单的实体：`id`（UUID 主键）、`displayName`、`createdAt`。演示阶段不实现注册/登录，API 通过 `X-User-Id` header 传入 UUID 来标识用户。首次调用时自动创建。

### [ChatSession.java](backend/src/main/java/com/example/minagent/session/ChatSession.java)

核心实体：`id`、`user`（@ManyToOne）、`title`、`@Version version`（乐观锁）、`createdAt`、`updatedAt`。

`@Version` 乐观锁在 `update` 时自动比对版本号，防止并发写覆盖。

### [ChatMessage.java](backend/src/main/java/com/example/minagent/session/ChatMessage.java)

消息实体，`role` 枚举四种：

- `USER` — 用户输入
- `ASSISTANT` — 模型最终回答
- `ASSISTANT_TOOL_CALL` — 模型决策调用工具（存储 `tool_calls_json`）
- `TOOL` — 工具执行结果（存储 `tool_call_id` + `tool_name`）

每条消息有单调递增的 `sequenceNo`（per Session），保证消息顺序。提供了 4 个静态工厂方法：`user()`、`assistant()`、`assistantToolCall()`、`tool()`。

### [SessionSummary.java](backend/src/main/java/com/example/minagent/session/SessionSummary.java)

会话摘要实体，通过 `@MapsId` 与 `ChatSession` 一对一关联。存储压缩后的摘要文字和覆盖到的消息边界。

### [TodoItem.java](backend/src/main/java/com/example/minagent/session/TodoItem.java) / [AgentRun.java](backend/src/main/java/com/example/minagent/session/AgentRun.java)

这两个是 Java `record`（不可变数据载体），不是 JPA 实体。TodoItemRepository 使用 JdbcTemplate 直接操作。

### [SessionService.java](backend/src/main/java/com/example/minagent/session/SessionService.java)

**★ 关键服务**，负责：

1. **用户创建/查找** (`findOrCreateUser`)：不存在则新建
2. **Session CRUD**：创建、列表、按 ID 查询（带用户关联加载）
3. **所有权校验** (`requireOwnedSession`)：
   ```java
   ChatSession session = sessionRepository.findByIdWithUser(sessionId)
       .orElseThrow(() -> new SessionNotFoundException(...));
   if (!session.getUserId().equals(userId)) {
       throw new SessionNotFoundException(...); // 统一 404，避免泄露存在性
   }
   ```
4. **消息管理**：保存、查询（按 session）、获取最近 N 条、获取消息数、获取最大序号
5. **摘要管理**：保存、查询

### Repository 层

四个 JPA Repository 接口：

- `AppUserRepository` — 标准 CRUD
- `ChatSessionRepository` — `findByUserIdOrderByUpdatedAtDesc()` + `findByIdWithUser()`（JOIN FETCH 避免 N+1）
- `ChatMessageRepository` — `findBySessionIdOrderBySequenceNoAsc()`、`findRecentBySessionId()`、`maxSequenceNoBySessionId()`
- `SessionSummaryRepository` — `findBySessionId()`

#### 为什么用 `@Query` 而不是方法命名推导？

Spring Data JPA 的方法名推导无法穿透 `@ManyToOne` 关联属性。例如 `findByUserId` 无法解析 `ChatSession.user.id` 路径，必须用显式 JPQL：`select s from ChatSession s where s.user.id = :userId`。

### [TodoItemRepository.java](backend/src/main/java/com/example/minagent/todo/TodoItemRepository.java)

使用 `JdbcTemplate` 直接操作 `todo_item` 表，而非 JPA。原因：`TodoItem` 是 record 而非 `@Entity`，不需要 JPA 的变更追踪和懒加载。

三个核心操作：

- `create(userId, sessionId, content)` — INSERT + 返回新记录
- `findByUserAndSession(userId, sessionId)` — 严格按 userId + sessionId 双条件查询
- `complete(id, userId, sessionId)` — UPDATE status + completed_at，三道校验防止越权

---

## 5. LLM 接入 (`llm/`)

### DTO 设计

#### [ChatCompletionRequest.java](backend/src/main/java/com/example/minagent/llm/dto/ChatCompletionRequest.java)

完全遵循 OpenAI Chat Completions 协议的请求体：

```json
{
  "model": "qwen3.6-plus",
  "messages": [...],
  "tools": [...],
  "tool_choice": "auto",
  "temperature": 0.2,
  "enable_thinking": false
}
```

`enable_thinking=false` 是**关键设计决策**——百炼部分模型在非流式 Function Calling 下如果开启思考模式会报参数错误。

#### [ChatCompletionResponse.java](backend/src/main/java/com/example/minagent/llm/dto/ChatCompletionResponse.java)

嵌套结构：`Response → List<Choice> → AssistantMessage → (content | tool_calls) + Usage`

`reasoning_content` 字段只在 DTO 中声明用于兼容协议，**不持久化、不返回前端**。这是安全设计：防止模型隐藏思维链（CoT）泄露。

#### [LlmMessage.java](backend/src/main/java/com/example/minagent/llm/dto/LlmMessage.java)

一个 `record` 支持四种角色的消息构造：

- `system(String)` — 系统提示词
- `user(String)` — 用户输入
- `assistant(String)` — 纯文本回答
- `assistantToolCalls(List<LlmToolCall>)` — 工具调用决策
- `tool(String callId, String name, String content)` — 工具结果，`tool_call_id` 与 assistant 消息配对

所有 JSON 可空字段用 `@JsonInclude(NON_NULL)` 处理，避免序列化 null 值到请求体。

### [BailianLlmGateway.java](backend/src/main/java/com/example/minagent/llm/BailianLlmGateway.java) ★

**核心 LLM 客户端**。实现流程：

```
1. 构造 ChatCompletionRequest
2. WebClient POST → {baseUrl}/chat/completions
3. onStatus 拦截：
   ├── 401/403 → Mono.error(AuthException) —— 不重试
   ├── 429     → Mono.error(RateLimitedException)
   └── 5xx     → Mono.error(LlmServiceException)
4. bodyToMono(ChatCompletionResponse.class)
5. .block() 阻塞获取结果
6. 异常时指数退避重试（最多 maxRetries 次）
```

**重试策略核心代码：**

```java
while (attempts <= maxRetries) {
    try { return doChat(request); }
    catch (AuthException e) { throw e; }          // 不重试
    catch (RateLimitedException e) {              // 429 重试
        attempts++;
        sleepWithBackoff(attempts);               // 500ms, 1500ms, ... + 随机抖动
    }
    catch (LlmServiceException e) {
        if (isRetryable(e)) { /* 同上 */ }
        else { throw e; }
    }
}
```

**为什么用 `Mono.error()` 而不是 `bodyToMono().flatMap(body → Mono.error())`？**

之前的实现用 `bodyToMono(String.class).flatMap(body → Mono.error(...))`，但当响应体为空（如 401）时 `bodyToMono` 返回 `Mono.empty()`，`flatMap` 不执行，错误不触发。改为直接 `Mono.error(new XxxException(...))` 后无需依赖响应体。

### 异常层次结构

```
RuntimeException
├── LlmServiceException (可重试：408/429/502/503/504/timeout)
│   └── RateLimitedException (429 专用)
└── AuthException (401/403，不重试，提示检查配置)
```

---

## 6. Agent 核心 (`agent/`) ★

### [AgentDecision.java](backend/src/main/java/com/example/minagent/agent/AgentDecision.java)

使用 Java 21 的 `sealed interface` 定义三种决策类型：

```java
public sealed interface AgentDecision
    permits FinalAnswerDecision, ToolCallsDecision, InvalidDecision {

    record FinalAnswerDecision(String answer) implements AgentDecision {}

    record ToolCallsDecision(String reasoningSummary, List<RequestedToolCall> calls)
        implements AgentDecision {}

    record InvalidDecision(String reason, String rawResponseExcerpt)
        implements AgentDecision {}

    record RequestedToolCall(String callId, String toolName, JsonNode arguments) {}
}
```

`sealed interface` 确保编译器可以在 `switch` 表达式中穷举检查所有分支。

### [LlmOutputParser.java](backend/src/main/java/com/example/minagent/agent/LlmOutputParser.java)

**解析优先级：**

1. `choices` 为空 → `InvalidDecision("Empty response")`
2. `tool_calls` 非空 → 逐个解析 `function.name` 和 `function.arguments`（JSON 字符串转 JsonNode），生成 `ToolCallsDecision`
3. `tool_calls` 为空且 `content` 非空 → `FinalAnswerDecision`
4. 两者都为空 → `InvalidDecision`
5. arguments 不是合法 JSON → 记录错误，跳过该 tool call，不崩溃

### [AgentRuntime.java](backend/src/main/java/com/example/minagent/agent/AgentRuntime.java) ★★★

**整个项目最核心的文件。** 实现了具备感知→决策→行动→观察→终止能力的自研 Agent 循环。

#### 主循环 `run(AgentRunCommand)` 的完整流程：

```
1. Session 所有权校验 → requireOwnedSession(userId, sessionId)
2. 获取 Session 锁    → sessionLock.acquire(sessionId)  // 失败抛 409
3. 保存用户消息       → ChatMessage.user(...)
4. 构建 Context       → buildContext(sessionId)
   ├── System Prompt（固定文本）
   ├── Session Summary（如果存在）
   ├── 最近 12 条历史消息（反序后按时间正序）
   └── 当前用户输入
5. Loop (最多 maxSteps=8 次):
   ├── llmGateway.chat(messages, toolRegistry.definitions())
   ├── outputParser.parse(response) → AgentDecision
   │
   ├── [FinalAnswerDecision]
   │   └── 保存 ASSISTANT 消息 → 返回 COMPLETED
   │
   ├── [InvalidDecision]
   │   ├── 第一次 → 追加修复 system message → continue
   │   └── 第二次 → 保存错误消息 → 返回 ERROR
   │
   └── [ToolCallsDecision]
       ├── 保存 ASSISTANT_TOOL_CALL 消息（含 tool_calls_json）
       ├── 对每个 tool call:
       │   ├── toolExecutor.execute(call, ctx) → ToolResult
       │   ├── 保存 TOOL 消息（含 tool_call_id 配对）
       │   └── 追加到 workingMessages
       └── continue loop
6. 超过 maxSteps → 返回 MAX_STEPS
7. finally: sessionLock.release()
```

#### Context 构建 `buildContext(sessionId)`：

```
System Prompt（固定）
  → Session Summary（如有）
  → 最近消息（按 sequenceNo 正序）
     ├── USER: 原样恢复
     ├── ASSISTANT: 原样恢复
     ├── ASSISTANT_TOOL_CALL: 反序列化 tool_calls_json → LlmMessage.assistantToolCalls()
     └── TOOL: 恢复为 LlmMessage.tool(callId, toolName, content)
```

`ASSISTANT_TOOL_CALL` 消息中存储的是序列化的 `List<LlmToolCall>` JSON，加载时需要反序列化还原为对象，以确保回传给 LLM 的 `tool_calls` 格式正确与 `tool_call_id` 匹配。

### [SessionRunLock.java](backend/src/main/java/com/example/minagent/agent/SessionRunLock.java)

基于 `ConcurrentHashMap<UUID, ReentrantLock>` 的 keyed lock：

```java
public boolean acquire(UUID sessionId) {
    ReentrantLock lock = locks.computeIfAbsent(sessionId, k -> new ReentrantLock());
    return lock.tryLock();  // 非阻塞，拿不到立即返回 false → 409
}
```

单实例部署够用；多实例扩展时替换为 PostgreSQL advisory lock。

### [AgentRunCommand.java](backend/src/main/java/com/example/minagent/agent/AgentRunCommand.java) / [AgentRunResult.java](backend/src/main/java/com/example/minagent/agent/AgentRunResult.java)

输入输出 DTO：

- `AgentRunCommand(userId, sessionId, content)` — 从 Controller 传入
- `AgentRunResult(runId, messageId, answer, status)` — 返回给 Controller，含三种工厂方法 `completed()`、`maxSteps()`、`error()`

---

## 7. 工具系统 (`tool/`)

### [AgentTool.java](backend/src/main/java/com/example/minagent/tool/AgentTool.java)

所有工具的统一接口：

```java
public interface AgentTool {
    ToolDefinition definition();                          // 返回工具的 JSON Schema
    ToolResult execute(JsonNode arguments, ToolContext context);  // 执行工具调用
}
```

### [ToolDefinition.java](backend/src/main/java/com/example/minagent/tool/ToolDefinition.java)

```java
public record ToolDefinition(
    String name,              // 工具名，全局唯一
    String description,       // 给 LLM 看的功能说明
    ObjectNode parametersSchema  // OpenAI Function Calling 的 parameters JSON Schema
) {}
```

### [ToolContext.java](backend/src/main/java/com/example/minagent/tool/ToolContext.java)

```java
public record ToolContext(UUID userId, UUID sessionId, UUID runId) {}
```

**安全关键点**：`userId`、`sessionId`、`runId` 全部由 Runtime 在服务端注入，永远不出现在 LLM 可控参数中。模型无法伪造其他用户或其他 Session 的 ID 来越权访问数据。

### [ToolResult.java](backend/src/main/java/com/example/minagent/tool/ToolResult.java)

```java
public record ToolResult(
    boolean success,          // 执行是否成功
    String code,             // OK / INVALID_ARGUMENTS / UNKNOWN_TOOL / TOOL_ERROR / ...
    JsonNode data,           // 结构化结果数据（用于前端展示）
    String modelMessage      // 给 LLM 看的自然语言消息（进入下一次 chat 的 context）
) {}
```

关键设计：`data` 和 `modelMessage` 分离。`data` 是 raw JSON 供 Trace 展示，`modelMessage` 是自然语言供 LLM 理解。

### [ToolRegistry.java](backend/src/main/java/com/example/minagent/tool/ToolRegistry.java)

```java
@Component
public class ToolRegistry {
    private final Map<String, AgentTool> tools;  // name → tool

    public ToolRegistry(List<AgentTool> discoveredTools) {
        // Spring 自动注入所有 AgentTool Bean
        // 使用 LinkedHashMap 保持注册顺序
        // 重复名称抛 IllegalStateException
    }

    public List<LlmToolDefinition> definitions() { ... }   // 转为 OpenAI 格式
    public AgentTool require(String name) { ... }           // 按名查找，不存在抛异常
}
```

Spring 通过构造函数注入 `List<AgentTool>` —— **自动发现所有实现了 AgentTool 的 Bean**。这是框架唯一的黑魔法，其余逻辑全是手写。

### [ToolSchemaValidator.java](backend/src/main/java/com/example/minagent/tool/ToolSchemaValidator.java)

**每次工具执行前的参数校验**，拦截项：

1. `additionalProperties=false` → 拒绝未定义字段
2. `required` 字段缺失 → 拒绝
3. 类型不匹配（string/number/integer/boolean/object/array）→ 拒绝
4. 字符串长度超限（minLength/maxLength）→ 拒绝
5. 数字范围超限（minimum/maximum）→ 拒绝
6. 枚举值不匹配 → 拒绝

校验失败返回 `Optional.of(errorMessage)`，校验通过返回 `Optional.empty()`。**失败不抛异常**，而是转为结构化的 `INVALID_ARGUMENTS` observation，让模型有机会修正参数重试。

### [ToolExecutor.java](backend/src/main/java/com/example/minagent/tool/ToolExecutor.java)

统一执行入口：

```
1. registry.require(toolName) → 找到工具
2. validator.validate(def, args) → 校验参数
3. tool.execute(args, ctx) → 执行
4. 结果 > 32KB → 截断 + 标记 [truncated]
5. 异常 → 封装为 ToolResult.failure(...)，不抛裸异常
```

所有异常（UnknownTool、工具内部错误）都转为结构化 `ToolResult`，Runtime 将它们作为 `role=tool` 消息回传 LLM，模型可以读错误消息并调整策略。

---

## 8. 四个工具实现 (`tool/impl/`)

### [ExpressionParser.java](backend/src/main/java/com/example/minagent/tool/impl/ExpressionParser.java)

**递归下降解析器**，不依赖任何外部库或 `ScriptEngine`。

文法：

```
expression → term (('+' | '-') term)*
term       → factor (('*' | '/') factor)*
factor     → ('+' | '-')? factor | '(' expression ')' | number
number     → digit+ ('.' digit+)?
```

**实现细节：**

- 使用 `BigDecimal` 保证精确算术（不用 `double`）
- 除法：`left.divide(right, 20, HALF_UP)` — 20 位小数精度
- 除零检测：`DIVISION_BY_ZERO` 特定错误码
- 非法字符在构造函数中检查，通过白名单 `[0-9+\-*/() .]` 过滤
- `stripTrailingZeros()` 去除无意义尾零

### [CalculatorTool.java](backend/src/main/java/com/example/minagent/tool/impl/CalculatorTool.java)

工具实现，参数 Schema 要求一个 `expression` 字符串（1-200 字符）。`execute()` 方法委托 `ExpressionParser.parse()`，异常映射为 `ToolResult.failure(DIVISION_BY_ZERO | INVALID_EXPRESSION, message)`。

### [MockSearchTool.java](backend/src/main/java/com/example/minagent/tool/impl/MockSearchTool.java)

数据源：[search-documents.json](backend/src/main/resources/mock/search-documents.json) — 6 篇关于 Agent Loop、Spring Boot、React、Function Calling、Context、Session 的内置文档。

**搜索算法：**

- 输入 query 进行分词（按空格和标点切分，保留 CJK 字符）
- 对每篇文档计分：关键词命中 ×3、标题命中 ×2、正文命中 ×1（大小写不敏感）
- 按分数降序，取前 N 条（limit 范围 1-5）
- 相同输入 → 相同结果（确定性），保证演示和测试稳定

**设计要点**：`@PostConstruct loadDocuments()` 在启动时加载 JSON 数据到内存 `List<SearchDocument>`，搜索时是纯内存操作，不访问网络，满足安全要求。

### [WeatherTool.java](backend/src/main/java/com/example/minagent/tool/impl/WeatherTool.java)

`Map<String, WeatherData>` 存储北京/上海/杭州/深圳四城的固定天气数据。未知城市返回 `CITY_NOT_FOUND` + 可查询城市列表。

### [TodoTool.java](backend/src/main/java/com/example/minagent/tool/impl/TodoTool.java)

三个操作：

- `create` — 必须提供 `content`，新建 OPEN 状态待办
- `list` — 查询当前 session 所有待办（强制 userId + sessionId 过滤）
- `complete` — 必须提供 `todoId`（UUID 格式），UPDATE 时三道校验（id + userId + sessionId）

**安全设计**：所有数据库操作强制带 `userId + sessionId`。即使 LLM 幻觉构造了其他用户的 todoId，也无法完成/查询。

---

## 9. 上下文管理 (`memory/`)

### [TokenEstimator.java](backend/src/main/java/com/example/minagent/memory/TokenEstimator.java)

粗略 token 估算（用于判断是否需要压缩，非精确计费）：

```java
int estimate(String text) {
    long cjk = text.codePoints()
        .filter(cp → UnicodeScript.of(cp) == HAN).count();    // CJK 字符 ≈ 1 token
    long nonCjk = text.length() - cjk;                        // 非 CJK ≈ 0.25 token
    return cjk + Math.ceilDiv(nonCjk, 4);
}
```

### [ContextAssembler.java](backend/src/main/java/com/example/minagent/memory/ContextAssembler.java)

构建 LLM 请求的 `messages` 列表。组装顺序：

1. **System Prompt** — 固定文本（工作规则 + 安全要求）
2. **Session Summary** — 如果存在，作为独立 system message
3. **最近 N 条消息** — 从数据库加载（DESC 查询），反序后按时间正序加入

关键：`ASSISTANT_TOOL_CALL` 消息需反序列化 `tool_calls_json` 还原为 `List<LlmToolCall>` 对象，保证 `tool_call_id` 与后续的 `role=tool` 消息配对正确。

### [ContextCompressor.java](backend/src/main/java/com/example/minagent/memory/ContextCompressor.java)

触发条件：

- `countMessages > compressMessageThreshold`（30 条）
- `estimateContextTokens > contextTokenBudget`（12000）

目前 MVP 实现为日志记录 + 摘要构建，实际 LLM 摘要调用在后续迭代中完善。压缩失败不阻断当前对话（catch 后仅 warn 日志）。

---

## 10. API 层 (`api/`)

### [SessionController.java](backend/src/main/java/com/example/minagent/api/SessionController.java)

| 端点                                | 方法             | 说明                               |
| ----------------------------------- | ---------------- | ---------------------------------- |
| `POST /api/sessions`              | 创建会话         | 从`X-User-Id` header 读取 userId |
| `GET /api/sessions`               | 列出用户所有会话 | 按 updatedAt 降序                  |
| `GET /api/sessions/{id}`          | 获取会话详情     | 必须 ownership 校验                |
| `GET /api/sessions/{id}/messages` | 获取会话消息列表 | 按 sequenceNo 升序                 |

### [AgentController.java](backend/src/main/java/com/example/minagent/api/AgentController.java)

| 端点                                    | 方法                  | 说明                         |
| --------------------------------------- | --------------------- | ---------------------------- |
| `POST /api/sessions/{id}/messages`    | 发送消息（JSON 响应） | 同步返回`AgentRunResponse` |
| `POST /api/sessions/{id}/runs:stream` | 发送消息（SSE）       | 异步事件流                   |

**SSE 实现**：使用 `SseEmitter(120_000L)`（2 分钟超时），在 `ExecutorService.newVirtualThreadPerTaskExecutor()` 中异步执行 Agent Runtime，事件类型：`run_started` → `answer_delta` → `run_finished` | `error`。

### [ToolController.java](backend/src/main/java/com/example/minagent/api/ToolController.java) / [TodoController.java](backend/src/main/java/com/example/minagent/api/TodoController.java)

- `GET /api/tools` — 返回所有工具的 name、description、JSON Schema
- `GET /api/sessions/{id}/todos` — 查询当前会话待办（强制 ownership 校验）

### [ApiExceptionHandler.java](backend/src/main/java/com/example/minagent/api/ApiExceptionHandler.java) ★

统一异常处理映射：

| 异常类型                            | HTTP 状态码 | 响应 code             | 说明                       |
| ----------------------------------- | ----------- | --------------------- | -------------------------- |
| `SessionNotFoundException`        | 404         | `SESSION_NOT_FOUND` | Session 不存在或不属于用户 |
| `SessionBusyException`            | 409         | `SESSION_BUSY`      | 同 Session 并发            |
| `MethodArgumentNotValidException` | 400         | `VALIDATION_ERROR`  | 参数校验失败               |
| `AuthException`                   | 502         | `LLM_AUTH_ERROR`    | 百炼 401/403               |
| `LlmServiceException`             | 503         | `LLM_SERVICE_ERROR` | 百炼 429/5xx/timeout       |
| `Exception`                       | 500         | `INTERNAL_ERROR`    | 兜底未知错误               |

所有错误响应格式统一：

```json
{ "code": "xxx", "message": "xxx", "requestId": "uuid", "timestamp": "..." }
```

安全要求：对外错误不泄露堆栈、API Key、System Prompt、其他会话数据。使用 `requestId` 关联服务端日志。

---

## 11. 关键设计决策总结

| 决策                                               | 原因                                                     |
| -------------------------------------------------- | -------------------------------------------------------- |
| 不使用 Spring AI / LangGraph 等框架                | 题目核心要求"从零实现"，框架封装会遮蔽关键实现           |
| `enable_thinking=false`                          | 百炼部分模型在非流式下开启思考模式会报参数错误           |
| WebClient 而非 RestTemplate                        | Spring Boot 3.x 主流选择，支持响应式但不需要引入额外依赖 |
| JPA +`@Query` 而非 spring-data method derivation | 关联属性路径 method name 无法解析，必须显式 JPQL         |
| `timestamp` 而非 `timestamptz`                 | H2 测试兼容性，MVP 不依赖时区精度                        |
| `@Version` 乐观锁 + keyed ReentrantLock          | 数据库层 + 应用层双重并发控制                            |
| ExpressionParser 自研                              | 不依赖 ScriptEngine/JavaScript 引擎，安全可控            |
| `userId + sessionId` 双重校验                    | 防止 Session 串线和越权                                  |
| `ToolContext` 服务端注入                         | 模型永远无法控制 userId/sessionId/runId                  |
| sealed interface + record                          | Java 21 新特性，编译期穷举检查，不可变数据               |
| 异常转 ToolResult 而非裸抛                         | 让 LLM 有机会阅读错误并修正参数                          |
