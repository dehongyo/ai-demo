# Minimal Agent

从零实现的最小可用 Agent Runtime — 不依赖任何 Agent 框架，自行实现循环、工具注册、输出解析和上下文管理。

## 功能截图

### 架构概览

```
用户浏览器 → React UI → Spring Boot REST/SSE API → AgentRuntime
                                                        ↓
                                                  BailianLlmGateway → 阿里云百炼
                                                        ↓
                                                  ToolRegistry → 4 个工具
                                                        ↓
                                                  PostgreSQL (Session, 消息, Trace)
```

## 题目要求完成度

| 要求 | 状态 | 证据 |
|------|------|------|
| 从零实现核心 Agent Runtime | ✅ | `agent/AgentRuntime.java` — 自研循环 |
| 接收用户输入 | ✅ | `POST /api/sessions/{id}/messages` |
| 判断直接回复或调用工具 | ✅ | `LlmOutputParser` — content/tool_calls 解析 |
| 调用工具 | ✅ | `ToolExecutor` + `ToolRegistry` |
| 根据工具结果继续或返回 | ✅ | tool observation → 下一轮 loop |
| 至少三个工具 | ✅ | calculator, mock_search, weather, todo (4个) |
| 工具名称、描述、参数 Schema | ✅ | `GET /api/tools` |
| LLM 基于 Schema 自主决策 | ✅ | `tool_choice=auto`, 百炼 Function Calling |
| 输出解析 | ✅ | `LlmOutputParser` + `AgentDecision` sealed interface |
| 多 Session 隔离 | ✅ | userId + sessionId 双重校验 |
| 持续对话记忆 | ✅ | 消息持久化 + Session Summary |
| 纯对话追问 | ✅ | 最近 12 条消息进入 Context |
| 带工具追问 | ✅ | tool call + observation 成对持久化 |
| 最大轮次 | ✅ | `maxSteps=8` |
| Context 压缩 | ✅ | `ContextCompressor` — 消息阈值 + token 估算 |
| 基本异常处理 | ✅ | `ApiExceptionHandler` — 参数、工具、模型、数据库、限流 |
| 工具 Trace | ✅ | `TraceDrawer` — 前端展示 |
| 使用真实 LLM API | ✅ | 百炼 OpenAI-compatible API |
| 测试用例 | ✅ | 48 个后端测试 + 2 个前端测试 |

## 技术栈

**后端:** Java 21, Spring Boot 3.4.5, Spring MVC, WebClient, Spring Data JPA, Flyway, PostgreSQL 17, H2, Jackson, JUnit 5, MockWebServer, Testcontainers

**前端:** React 19.2, TypeScript (strict), Vite, React Router, Vitest, Testing Library

**基础设施:** Docker Compose (PostgreSQL)

## 架构图

```
┌──────────────────┬──────────────────────────────────┬───────────────────┐
│ Session 列表     │ 聊天区                           │ Trace 抽屉        │
│                  │                                  │                   │
│ + 新建会话       │ 用户消息                         │ Run #...          │
│ 天气与出行       │ Agent 回答                       │ Step 1: weather   │
│ 周报工作         │ Tool 执行卡片                    │ Step 2: todo      │
│                  │                                  │ token / 耗时      │
└──────────────────┴──────────────────────────────────┴───────────────────┘
```

## Agent Loop 说明

每次用户请求触发一个 Agent Run，每个 Run 最多 8 个 Step：

```
ReceiveInput → PersistUserMessage → BuildContext → CallLLM → ParseOutput
    → FinalAnswer (content 且无 tool_calls)
    → ValidateToolCall → ExecuteTool → AppendObservation → CheckLimit
    → RepairOnce (不可解析时修复一次)
```

关键循环规则：
- 同一 Session 同时只允许一个 Run
- 工具结果作为 `role=tool` 消息回传 LLM
- 工具异常转为结构化 observation，模型可修正参数重试
- 修复失败、达到 maxSteps 或安全失败才结束 Run

## 工具注册机制

所有 `AgentTool` 实现类由 Spring 自动发现，`ToolRegistry` 统一管理：

- `ToolRegistry`: 自动发现 + 去重 + 名称索引
- `ToolSchemaValidator`: 必填字段、类型、枚举、长度、范围校验
- `ToolExecutor`: 统一执行、计时、异常封装、结果大小限制

## Session 隔离方式

资源访问使用 `userId + sessionId` 双重校验：

```
sessionService.requireOwnedSession(userId, sessionId)
```

- 查不到时统一返回 404（避免泄露 Session 是否存在）
- Todo 查询强制带 userId + sessionId
- 前端通过 `X-User-Id` header 标识用户（演示用固定 UUID）

## Context 设计

**放入 Context：**
- System Prompt
- Session 压缩摘要
- 最近 12 条持久化消息
- 本次 Run 内的 tool call + observation

**不放入 Context：**
- 其他 Session/用户数据
- 完整 Trace、HTTP header、异常堆栈
- 原始隐藏思维链 (reasoning_content)
- API Key 或服务端配置

## Memory 召回时机与放置方式

| Memory | 召回时机 | 放置位置 |
|--------|---------|---------|
| Session summary | 每次 LLM 调用 | System Prompt 后的独立 system message |
| 最近消息 | 每次 LLM 调用 | 按原角色顺序 |
| assistant tool call + tool observation | 当前 Run 后续 Step | `role=assistant` tool_calls + `role=tool` |
| Todo 状态 | 仅当模型调用 todo 工具 | 工具结果 |
| Trace | 不召回 | 仅前端调试与审计 |

## Context 压缩

触发条件：
- Session 消息数 > 30
- Token 粗略估算 > 12,000

策略：保留最近 12 条消息，旧消息构建摘要；压缩失败不阻断当前对话。

## Trace 与异常处理

| 异常 | 用户结果 | 行为 |
|------|---------|------|
| 空消息/超长消息 | 400 | 不创建 run |
| Session 不存在/不属于用户 | 404 | 不调用 LLM |
| Session 正忙 | 409 | active run id |
| 百炼 401/403 | 502 | 立即停止，不重试 |
| 百炼 429/5xx | 503 | 有限重试后停止 |
| LLM 响应不可解析 | 修复一次 | 再失败安全停止 |
| 达到 maxSteps | 返回解释 | `MAX_STEPS` |

## 本地运行

```powershell
# 1. 复制环境变量
Copy-Item .env.example .env
# 编辑 .env 填入真实的 DASHSCOPE_API_KEY 和 BAILIAN_BASE_URL

# 2. 启动 PostgreSQL
docker compose up -d postgres

# 3. 启动后端
$env:DASHSCOPE_API_KEY="your-api-key"
$env:BAILIAN_BASE_URL="your-workspace-base-url"
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run

# 4. 启动前端 (新终端)
npm --prefix frontend install
npm --prefix frontend run dev
```

访问 `http://localhost:3000`

## 百炼 API Key 配置

1. 访问 [百炼控制台](https://bailian.console.aliyun.com/) 获取 API Key
2. 设置环境变量 `DASHSCOPE_API_KEY`
3. 设置 `BAILIAN_BASE_URL` 为北京地域业务空间 Base URL
4. API Key 绝不写入代码、配置文件、测试快照或 Git 历史

## 测试运行

```powershell
# 后端单元测试 + 集成测试
.\backend\mvnw.cmd -f backend\pom.xml clean verify

# 前端测试
npm --prefix frontend run test -- --run

# 真实百炼 API 冒烟测试 (需要 API Key)
$env:RUN_REAL_LLM_TESTS="true"
$env:DASHSCOPE_API_KEY="your-api-key"
$env:BAILIAN_BASE_URL="your-workspace-base-url"
.\backend\mvnw.cmd -f backend\pom.xml -Dtest=BailianRealApiSmokeTest test
```

## 演示场景

### 场景 A: 直接回答
> 用户: "你好，请介绍一下你能做什么。"
> 期望: 直接返回答案，0 次工具调用。

### 场景 B: 单工具
> 用户: "帮我计算 (12.5 + 7.5) * 3。"
> 期望: calculator 自主选择，结果为 60。

### 场景 C: 多工具串联
> 用户: "查一下杭州天气，如果下雨就帮我记一个出门带伞的待办。"
> 期望: weather → todo.create → final answer。

### 场景 D: Session 隔离
> 窗口 1: "查杭州天气并记待办" → 窗口 2: "写周报并记待办"
> 期望: 两个窗口的 todo 互不出现。

## 已知限制与可扩展方向

- 当前为 MVP，不包含多 Agent 协作
- 无向量数据库或完整 RAG
- 无浏览器/Shell 等高风险工具
- 单实例部署（lock 为 JVM 级别）
- 可扩展: JWT 登录、流式输出、OpenTelemetry、多实例 advisory lock

## AI Prompt 与问题解决记录

- [docs/prompts.md](docs/prompts.md) — 所有 Prompt 全文及版本管理
- [docs/problem-solving.md](docs/problem-solving.md) — 开发过程中的关键问题与解决方案

## 项目结构

```
minimal-agent/
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/example/minagent/
│       ├── MinimalAgentApplication.java
│       ├── api/          # REST/SSE 控制器
│       ├── agent/        # Agent 循环、输出解析
│       ├── config/       # 配置属性
│       ├── llm/          # 百炼 API 客户端
│       ├── memory/       # 上下文组装与压缩
│       ├── session/      # Session、消息实体
│       ├── todo/         # 待办 Repository
│       └── tool/         # 工具接口、注册、执行
├── frontend/
│   └── src/
│       ├── api/          # API 客户端
│       ├── components/   # React 组件
│       ├── hooks/        # 自定义 Hooks
│       ├── pages/        # 页面组件
│       └── types/        # TypeScript 类型
├── docker-compose.yml
└── docs/
```
