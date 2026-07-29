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

---

# 架构设计问答

## 模块一：Context / Performance

### Q1: 第一轮长窗口或多模态输入时 first token 5-10s → 2s，如何优化？

**成本约束：** 快速、低成本、用户体验不差。

**核心思路：** 不在模型推理侧做文章（那是百炼的事），而是在**提示结构**和**预处理**上做优化。

**方案 A：Prefix Caching（KV Cache 复用）—— 零成本，效果显著**

System Prompt 在所有请求中是固定的。OpenAI-compatible API 大多支持自动检测公共前缀并复用 KV Cache。你的 System Prompt 约 400 中文字符，如果每次请求的 system message 完全一致且在 messages 数组最前面，百炼会自动命中缓存，跳过重复计算。

实现要点：
- 固定 System Prompt 始终放在 `messages[0]`
- 不要在 System Prompt 前插入变化的 system message（如摘要）
- 摘要作为 `messages[1]`，前缀仍可复用

预期收益：省掉 System Prompt 的 prefill 时间，约减少 1-2s。

**方案 B：Progressive Context Loading（渐进式上下文）—— 体验提升最明显**

第一轮不要一股脑把完整上下文塞进去。先给最小上下文让模型快速出 first token，然后在流式输出过程中异步补全：

```
Phase 1（<200ms 内发出）:
  messages = [system_prompt, user_question]
  → 模型立刻出 first token，用户看到正在打字

Phase 2（在模型输出过程中，异步完成）:
  - 补全最近对话历史
  - 补全检索到的文档片段
  - 补全工具 Schema
  → 后续 token 基于完整上下文
```

这不减少总延迟，但用户**感知到的等待时间**从 5s 降到了几百毫秒——模型已经开始"思考"了，用户就不会觉得卡。

**方案 C：Prompt Compression（离线预压缩）—— 事前做，不占用请求时延**

在用户发送消息之前（如切换到该 Session 时），后台线程提前把上下文压缩好：

```java
// 用户打开 Session 时就触发，不是发消息时才做
CompletableFuture.runAsync(() -> {
    String compressed = contextCompressor.compress(sessionId);
    redis.set("context:" + sessionId, compressed, Duration.ofMinutes(5));
});
```

到真正发消息时，上下文已经是最精简的版本了。

**综合推荐：A（免费）+ B（体验好）同时上。** Prefix Caching 让实际 prefill 变短，渐进上下文让用户感知不到剩余延迟。

---

### Q2: 200 轮对话后 Context 压缩方案，如何确保流畅？

**压缩策略：分三层处理**

```
Layer 1: 最近 10-15 条 —— 保留原文（支持代词追问和短程语义）
Layer 2: 中间 20-150 条 —— LLM 摘要压缩（保留关键事实和用户偏好）
Layer 3: 最旧 50 条 —— 结构化提取（只保留"用户长期记忆"条目）
```

**Layer 3 的"结构化提取"是关键创新：**

普通摘要的问题是：压缩再压缩，信息会像传话游戏一样失真。更好的方式是**把旧对话从自然语言转成结构化事实**：

```
原始对话（150 轮前）:
User: "我下周要出差去上海，帮我看看天气"
Agent: "上海下周多云，15-22°C"
User: "那帮我记一下，出差记得带正装"

↓ 不存摘要文本，存结构化记录：

{
  "facts": [
    {"type": "user_info", "content": "用户下周出差去上海"},
    {"type": "user_preference", "content": "出差需要带正装"},
    {"type": "confirmed_action", "content": "已查询上海天气：多云 15-22°C"}
  ]
}
```

**确保流畅性的四个机制：**

| 机制 | 作用 |
|------|------|
| 显式指代解析 | 摘要中记录 `"它"指代"上海天气"`，防止压缩后指代断裂 |
| 边界标记 | `covered_until_message_id` 精确记录哪些消息已被压缩，不重复不遗漏 |
| 增量压缩 | 新摘要 = 旧摘要 + 新增待压缩消息，不全量重压，避免信息逐步衰减 |
| 降级保护 | 压缩失败时保留原文，下次再尝试；极端情况下宁可 context 长一点也不丢失信息 |

---

## 模块二：Memory

### Q1: 半个月后用户问了一个以前问过的问题，Agent 如何做 memory 召回？

**问题本质：** 这是长程 Memory 检索问题。用户和 Agent 聊了半个月，可能有几千条消息、上百个 session。不能用简单的"最近 N 条"策略了。

**合理的召回方案：混合检索 + 重排序**

```
用户当前问题: "我上次说的那个项目部署方案是什么来着？"
         │
         ▼
┌──────────────────────────────────────┐
│ Step 1: 多路召回（并行）              │
│                                      │
│ ① 关键词匹配 → SQL/ES 全文检索        │
│    "项目" "部署" "方案" → 50 条候选    │
│                                      │
│ ② 语义向量 → Embedding 相似度搜索     │
│    user query 向量 vs 历史消息向量     │
│    → 50 条候选                        │
│                                      │
│ ③ 时间加权 → 最近 7 天加权提升        │
│ ④ 用户显式偏好 → 用户曾经标记"记住这个"│
└──────────────────────────────────────┘
         │
         ▼ 合并去重 ~80 条
┌──────────────────────────────────────┐
│ Step 2: 粗排 → 小模型打分            │
│ BGE-reranker 或 Cross-Encoder        │
│ → Top 10                             │
└──────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────┐
│ Step 3: LLM 精排 + 摘要              │
│ "以下是与用户问题相关的历史记录…"      │
│ → 3-5 条最相关上下文 + 简短摘要        │
│ → 注入当前对话 Context                │
└──────────────────────────────────────┘
```

**为什么不直接把向量搜索的第一名喂给 LLM？**

向量相似 ≠ 语义相关。"项目部署"的向量和"项目失败"的向量很接近，但用户问前者你给后者就是答非所问。**Reranker 是召回质量的分水岭**——用 BGE-reranker-v2 这样的小模型做精排，成本低、效果好。

**MVP 可落地的简化版：**

- 所有历史消息在保存时做一次 Embedding（用百炼的 Embedding API，极便宜）
- 存到 PostgreSQL 的 `pgvector` 字段（已有 PostgreSQL，加个扩展即可）
- 用户提问时，Embedding 查 Top 5 + 时间衰减（越久远权重越低）
- 直接注入 Context，标注 `[历史相关记忆]` 来源

---

### Q2: Agent Memory 经典框架与发展趋势

**经典框架：MemGPT / Letta 的三层记忆模型**

这是目前学界和工业界共识度最高的 Agent Memory 架构：

```
┌─────────────────────────────────────────────┐
│ Working Memory（工作记忆）                   │
│ = LLM 的 Context Window                    │
│ 容量：~128K tokens                          │
│ 特点：当前对话直接可见，最高保真度           │
│ 内容：System Prompt + 最近消息 + 工具结果    │
├─────────────────────────────────────────────┤
│ Episodic Memory（情节记忆）                 │
│ = 压缩后的对话摘要 + 关键事实                │
│ 容量：无限（存数据库）                       │
│ 特点：被召回时注入 Working Memory           │
│ 内容：Session Summary + 结构化事实           │
├─────────────────────────────────────────────┤
│ Semantic Memory（语义记忆）                 │
│ = 向量化的知识库 + 用户偏好                  │
│ 容量：无限（向量数据库）                     │
│ 特点：语义相似度检索                         │
│ 内容：Embedding + 元数据 + 时间衰减权重      │
└─────────────────────────────────────────────┘
```

这个模型的核心洞察：**LLM 的 Context Window 是"意识"，数据库是"记忆"。意识只有当前焦点，记忆需要被检索才能进入意识。**

**头部玩家的做法：**

| 玩家 | 内存方案 | 亮点 |
|------|---------|------|
| **OpenAI (ChatGPT)** | 长期记忆 + 自动摘要 | 2024 年上线的 Memory 功能，自动提取用户说过的偏好并存储，用户可管理（删除/编辑）。不依赖向量搜索，而是靠 LLM 在对话中判断"这段话是否值得记住"。关键是——让用户**可见、可管理** |
| **Anthropic (Claude)** | Project Knowledge + 长期记忆 | Claude 的 Projects 功能允许上传文档作为项目知识库。2025 年开始灰度长期记忆——Claude 会记住你的偏好、项目上下文。同样是用户**可见可管理**的设计 |
| **Google (Gemini)** | 无界上下文 + 自动记忆 | 利用 Google 的 TPU 算力优势，主打超长上下文（百万级 token）。倾向于"全量塞入"而非"检索召回" |
| **MemGPT / Letta** | 开源的三层记忆 | 学术界的标准实现。让 LLM 自己管理记忆的读写——LLM 决定什么该记住、什么时候该回忆。把记忆管理变成 Function Calling |

**发展趋势：**

1. **从"检索式"到"主动式"：** 不是等用户问了再去查，而是 Agent 在对话中**自动判断**"这段话值得记住"并提取存储（OpenAI Memory 就是这个思路）
2. **从"单一向量"到"多元存储"：** 结构化事实（知识图谱边）> 向量 Embedding > 原始文本。不同信息用不同存储方式
3. **用户可见性成为刚需：** 记忆不是黑盒——用户需要看到 Agent 记住了什么、能删除、能纠正。这是信任的基础
4. **Agentic Memory Manager：** 不在 Runtime 里硬编码记忆逻辑，而是由一个专门的"记忆管理 Agent"来决策何时存储、何时召回

---

## 模块三：Task

### Q1: 长程任务中模型忘掉目标，有哪些解决方案？

**问题本质：** LLM 的注意力随上下文距离衰减。任务目标在 messages[0]（System Prompt），但 Agent 已经执行了 20 步，当前注意力全在最近几步的工具结果上——"初心"被淹没了。

**方案一：Repeated Goal Injection（目标重复注入）—— 最简单**

每 N 步（如每 3 步）在 System Prompt 位置追加一行：

```
[当前任务目标]: 每天早上 9 点根据昨天聊天情况做复盘总结
[已完成步骤]: 1. 获取聊天记录 ✓  2. 分类整理 ✓
[当前步骤]: 3. 生成总结报告 ← 进行中
```

- **优：** 零成本、实现简单、效果立竿见影
- **缺：** 占用 token，对超复杂任务效果有限

**方案二：Checkpoint-Based Decomposition（检查点分解）—— 最结构化**

把长程任务拆成子任务，每个子任务完成后写 Checkpoint：

```
长程任务: T1 → T2 → T3 → T4 → T5
              ↑ 完成     ↑ 当前在 T3

不把 T1-T2 的所有细节带进 T3 的 Context，
而是把 T1-T2 的"结论摘要 + 产出物引用"作为 T3 的输入
```

- **优：** 上下文干净，每阶段只关注当前子任务
- **缺：** 需要预先分解——但可以让 LLM 自己分解

**方案三：External Task Tracker（外部任务跟踪器）—— 最可靠**

不在 LLM Context 里管理任务状态，而是存到数据库：

```sql
CREATE TABLE task_state (
    task_id UUID,
    goal TEXT,              -- 原始目标（不可变）
    current_phase INTEGER,  -- 当前阶段
    completed_steps JSONB,  -- 已完成步骤的摘要
    next_action TEXT,       -- 下一动作
    last_updated TIMESTAMP
);
```

每次 Agent 循环时，把 `task_state` 当前状态注入 Context。好处：即使 Agent 崩溃重启，任务状态不丢。

- **优：** 状态持久化、可审计、可恢复
- **缺：** 需要专门的状态更新逻辑

**方案四：Parallel Goal Reminder Agent（并行提醒 Agent）—— 最前沿**

在后台跑一个轻量级 Agent，职责只有一个：**监控主 Agent 的进展，判断是否偏离目标**。如果检测到偏离（如主 Agent 开始绕圈子、反复调同一个工具），注入纠正 Prompt：

```
[提醒]: 你当前的原始目标是"做复盘总结"。你已经在处理聊天记录，
        不要再搜索无关资料。请聚焦：从聊天记录提取关键信息 → 生成总结。
```

- **优：** 自动检测偏离，不需预定义检查点
- **缺：** 多了 LLM 调用成本

**我的推荐：方案一 + 方案三组合。** 每 3 步重复目标（免费的注意力强化），关键任务状态存数据库（防止上下文丢失不可恢复）。

---

### Q2: "每天早上 9 点根据昨天聊天情况做复盘总结"——如何设计？

**架构设计：Scheduled Task + Session-Aware Memory**

```
┌─────────────────────────────────────────────────────┐
│                    调度层                             │
│  Cron Trigger (每天 9:00)                            │
│  → CronCreate / Spring @Scheduled                    │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                   任务执行层                          │
│  DailyReviewTask.execute()                          │
│                                                      │
│  1. 查询昨天所有 Session 的消息                       │
│     SELECT * FROM chat_message                      │
│     WHERE created_at BETWEEN                        │
│       yesterday 00:00 AND yesterday 23:59            │
│     AND user_id = :userId                            │
│                                                      │
│  2. 按 Session 分组统计                               │
│     - 每个 Session 的消息数                           │
│     - 每个 Session 讨论的主题（LLM 提取）              │
│     - 完成的工具调用和结果                             │
│     - 未完成的事项（来自 Todo 表）                     │
│                                                      │
│  3. 预处理 + 压缩                                     │
│     每个 Session: 消息 → 摘要（避免原始数据撑爆)        │
│                                                      │
│  4. 调 LLM 生成复盘报告                              │
│     Prompt = 昨天的所有 Session 摘要 + 模板            │
│                                                      │
│  5. 持久化 + 通知                                     │
│     - 存到 daily_review 表                            │
│     - 可选: 推送到用户前端 / 邮件 / 新 Session         │
└─────────────────────────────────────────────────────┘
```

**关键设计决策：**

**① 为什么不直接把昨天全量消息喂给 LLM？**

一天可能有 500+ 条消息。先做 Per-Session 摘要再汇总——两阶段压缩。每个 Session 用 Context Compressor 压到 1500 字以内，汇总时 LLM 看到的是 ~10 个 Session 摘要，而不是 500 条原始消息。

**② 如何处理跨 Session 的关联？**

```
Session A: "帮我设计 Agent 的 Memory 模块"
Session B: "继续上次的 Memory 设计，加个向量检索"
Session C: "Memory 那个方案，我觉得召回还得加 Reranker"

三个 Session 讨论同一主题，复盘报告应该合并成:
"Memory 模块设计: (A) 确定三层架构 → (B) 加向量检索 → (C) 加入 Reranker 精排"
```

用 LLM 在汇总阶段做跨 Session 主题聚类和合并。

**③ 如何验证复盘质量？**

- 统计指标：覆盖率 = 有实质性内容的 Session 数 / 总 Session 数
- LLM 自评：让 LLM 给每段摘要打分（1-5），低于 3 分的重新生成
- 用户反馈：前端加 👍/👎 按钮，收集信号持续优化 Prompt

**④ 系统实现（Spring Boot）：**

```java
@Component
public class DailyReviewScheduler {
    
    @Scheduled(cron = "0 0 9 * * ?")  // 每天 9:00
    public void executeDailyReview() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        // 查昨天有活动的所有用户
        List<UUID> activeUsers = sessionService.findActiveUsers(yesterday);
        
        for (UUID userId : activeUsers) {
            // 异步执行每个用户的复盘
            CompletableFuture.runAsync(() -> {
                List<SessionDigest> digests = buildSessionDigests(userId, yesterday);
                String report = llmGateway.generateReview(digests);
                reviewRepository.save(new DailyReview(userId, yesterday, report));
            });
        }
    }
}
```

**⑤ 失败处理：**

- 部分 Session 摘要失败 → 跳过，报告中标注"Session X 无法总结"
- LLM 调用失败 → 重试 3 次，仍然失败则推迟到下次
- 整个复盘任务崩溃 → 第二天 9:00 重新触发，且补上昨天的（通过 `last_reviewed_date` 字段判断）

---

## 模块四：Tool / Session Runtime

### Q1: 异步工具执行 + 完成通知的设计

**场景：** Agent 调了一个工具（如 "生成周报 PDF"），这个工具需要 30 秒才能完成。不能让用户干等。

**设计：工具执行模型分两种**

```
同步工具（< 2s）                  异步工具（任意时长）
─────────────                    ─────────────────
calculator                      生成报告 PDF
weather                         发送邮件
mock_search                     CI/CD 触发构建
todo CRUD                       调用外部 API 等待审批
```

**异步工具的三阶段执行模型：**

```
Phase 1: Submit（提交）         Phase 2: Execute（执行）     Phase 3: Notify（通知）
─────────────────────          ─────────────────────      ──────────────────────
Agent 调工具                   后台线程执行                结果通知前端
↓                              ↓                          ↓
立即返回 taskId                真正干活                    WebSocket/SSE 推送
Agent 继续处理其他             完成后更新状态              Agent 决定: 继续等 / 稍后处理
不阻塞 loop                    持久化结果                 或: 开启新 Run 处理结果
```

**接口设计：**

```java
public interface AsyncAgentTool extends AgentTool {
    
    // 提交异步任务，立即返回 taskId（< 50ms）
    AsyncTaskHandle submit(JsonNode arguments, ToolContext context);
    
    // 查询任务状态（Agent 可以在后续 Step 中查询）
    AsyncTaskStatus getStatus(String taskId);
    
    // 获取任务结果（任务完成后调用）
    ToolResult getResult(String taskId);
}

public record AsyncTaskHandle(
    String taskId,          // 唯一任务标识
    AsyncTaskStatus status,  // SUBMITTED / RUNNING / COMPLETED / FAILED
    String estimatedDuration, // "约 30 秒"
    String pollUrl           // GET /api/tasks/{taskId} 查询状态
) {}
```

**LLM 视角：**

```
Step 3: Agent 调 generate_report → 工具返回:
  {"taskId":"task_abc","status":"SUBMITTED","estimatedDuration":"约30秒"}
  
Step 4: Agent 判断 → "报告正在生成，我先告诉你进度，30秒后再查结果"
  → 给用户: "报告生成中，预计 30 秒完成。你可以先处理其他事。"
  → 不调用其他工具，返回 FinalAnswer

Step 5: (用户 30 秒后发消息 "查一下报告好了没")
  → Agent 调 query_task_status(taskId=task_abc)
  → 返回: COMPLETED，结果是…
```

**前端通知机制：**

- SSE 事件 `task_completed`：前端收到后，如果用户正在看这个 Session，展示 Toast 通知
- 如果用户不在线：任务结果持久化，下次打开页面时在消息列表里看到 "报告已生成"

---

### Q2: Session State 为 Busy 时的冲突处理

**冲突矩阵：**

| 事件 | Session 状态 | Runtime 行为 | 返回 |
|------|-------------|-------------|------|
| 用户发新消息 | BUSY (上一次 Run 还在执行) | 拒绝，不创建新 Run | 409 `SESSION_BUSY` |
| 用户发新消息 | BUSY (异步工具在跑) | **允许**，异步工具不占锁 | 200 正常处理 |
| 异步工具完成 | BUSY (用户 Run 在执行) | **排队**，等当前 Run 完成后注入结果 | 不通知前端 |
| 异步工具完成 | IDLE | 创建新 Run，把结果作为上下文注入 | 推送 SSE 事件 |
| 同时两个异步工具完成 | IDLE | **合并通知**，同一条消息里列出两个结果 | 推送 SSE 事件 |

**关键设计：区分"Agent Run Busy"和"异步工具 Busy"**

```java
public enum SessionState {
    IDLE,           // 可以接收新请求
    RUNNING,        // Agent Loop 执行中，拒绝新消息 → 409
    ASYNC_PENDING   // 有异步工具在跑，但 Loop 已结束，可以接收新消息
}
```

Session State 机：

```
IDLE ──[用户发消息]──→ RUNNING ──[FinalAnswer]──→ IDLE
 │                        │
 │                        └──[异步工具提交]──→ RUNNING → ASYNC_PENDING
 │                                                    │
 │                    ┌───────────────────────────────┘
 │                    ▼
 └──[异步工具完成]── IDLE ←──[Agent 处理结果]── ASYNC_PENDING ←──[用户查结果]
```

**具体处理：**

```java
public void handleAsyncToolCompletion(String sessionId, ToolResult result) {
    if (sessionLock.tryAcquire(sessionId)) {
        try {
            // 当前空闲，创建新 Run 处理结果
            AgentRunCommand cmd = new AgentRunCommand(
                userId, sessionId,
                "[系统通知] 异步任务完成: " + result.modelMessage()
            );
            agentRuntime.run(cmd);  // Agent 看到结果，可能继续处理或通知用户
        } finally {
            sessionLock.release(sessionId);
        }
    } else {
        // 当前有 Run 在执行，把结果放到待处理队列
        pendingAsyncResults.put(sessionId, result);
        // 当前 Run 结束后，由 finally 块检查队列并处理
    }
}
```

**对已有代码的改动最小：**

当前 `SessionRunLock` 已经是 keyed lock。只需在 `AgentRuntime.run()` 的 `finally` 块末尾加检查待处理异步结果的逻辑即可。

---

## 模块五：Agent Runtime 架构对比

### Q1: Claude Code 工具输出 vs OpenAI-compatible Function Calling

**Claude Code 的工具输出方式（Tool Use / Computer Use）：**

Claude 的工具调用不是简单的 JSON 返回，而是**把工具结果作为对话消息的一部分原生嵌入在响应流中**。关键特征：

```
Claude 的响应格式（简化）:

{
  "content": [
    {"type": "text", "text": "让我看一下你的项目结构"},
    {"type": "tool_use", "id": "toolu_01", "name": "bash", "input": {"command": "ls"}}
  ]
}

用户/系统回复:

{
  "role": "user",
  "content": [
    {"type": "tool_result", "tool_use_id": "toolu_01", "content": "README.md\nsrc/\n"}
  ]
}
```

**OpenAI-compatible Function Calling（百炼 / GLM / 豆包）：**

```json
// 模型响应
{
  "choices": [{
    "message": {
      "role": "assistant",
      "content": null,
      "tool_calls": [
        {"id": "call_1", "type": "function", "function": {"name": "bash", "arguments": "{\"command\":\"ls\"}"}}
      ]
    }
  }]
}

// 用户回传结果
{
  "role": "tool",
  "tool_call_id": "call_1",
  "content": "README.md\nsrc/"
}
```

**核心差异与各自优缺点：**

| 维度 | Claude (Tool Use) | OpenAI (Function Calling) |
|------|------------------|--------------------------|
| **工具调用与文本的关系** | 同一消息内 text + tool_use 共存，模型可以**在解释的同时调工具** | tool_calls 和 content 互斥，模型要么说话要么调工具 |
| **多工具并行** | 同一消息内多个 tool_use 块，自然支持并行 | tool_calls 数组，也是并行，但语义上每个 tool call 是独立的 function 对象 |
| **工具结果的回传** | `tool_result` 作为 content block，与 tool_use_id 配对，结构统一 | 独立的 `role=tool` 消息，靠 tool_call_id 配对 |
| **流式（Streaming）** | 原生支持 content block 级别的流式——可以先发 text 再发 tool_use | tool_calls 的流式需要拼接 JSON 片段，实现更脆弱 |
| **细粒度控制** | 支持 `tool_choice` 的细粒度配置（any / auto / tool name） | `tool_choice` 只有 none / auto / required |

**各自设计的深层原因：**

Claude 设计成 content block 混合 text + tool_use，是因为 Anthropic 的 Agent 哲学是**"思考与行动一体"**——Agent 应该在推理的同时调用工具，tool_use 只是 content 的一种类型。这更接近人类的工作方式：边思考边动手。

OpenAI 的 `tool_calls` 与 `content` 互斥的分离设计，是为了**协议简单性和向后兼容**——聊天 API 的基础结构（message with content）不变，tool_calls 是附加的可选字段。代价是表达能力弱一些。

**实际影响（开发体验）：**

- 用 OpenAI 协议：你的代码需要处理 `content: null` 的情况（模型只调工具不说话），以及 `content` 不为 null 但 `tool_calls` 也为空的情况。解析器需要多几个分支。
- 用 Claude Tool Use：同一消息里既有 text 又有 tool_use，Parser 需要遍历 `content[]` 数组，对每个 block 分别处理。这更强大但也更复杂——你需要维护"当前在说什么 + 当前在调什么工具"两个维度的状态。

---

### Q2: OpenHands 的状态机设计优缺点及更优雅的实现

**OpenHands（原 OpenDevin）状态机：**

```
         ┌─────────┐
         │  INIT   │
         └────┬────┘
              │
    ┌─────────▼─────────┐
    │    THINKING       │ ← Agent 调用 LLM 思考
    └─────────┬─────────┘
              │
    ┌─────────▼─────────┐
    │     ACTION        │ ← 执行工具 / 输出内容
    └────┬────┬────┬────┘
         │    │    │
    ┌────▼┐ ┌▼──┐┌▼────┐
    │DONE │ │ERR││PAUSE│
    └─────┘ └───┘└─────┘
```

先思考 → 再行动 → 循环。思考步骤是一个独立的 LLM 调用，行动步骤是工具执行。这保证了每一步的 Trace 清晰。

**优点：**

| 优点 | 细节 |
|------|------|
| **Trace 可解释性极强** | 每一步是"思考"还是"行动"一目了然，审计友好 |
| **可暂停恢复** | PAUSE 状态意味着可以序列化整个 Agent 状态到磁盘，之后从同一个状态恢复 |
| **异常隔离** | ERR 状态集中处理，不会让工具异常污染 Agent 循环逻辑 |
| **可插拔的思考策略** | THINKING 可以用不同模型（如用最强模型思考，用便宜模型执行）|

**缺点：**

| 缺点 | 细节 | 影响 |
|------|------|------|
| **每步两次 LLM 调用** | THINKING + ACTION 各一次 | 延迟翻倍、成本翻倍 |
| **思考与行动割裂** | 思考时看不到工具结果，行动时不思考 | 对于需要"边做边调整"的任务效果差 |
| **状态爆炸** | 如果工具链有 N 个环节，状态机需要 N×M 个节点 | 维护成本指数增长 |
| **硬性边界** | THINKING 必须完成才能 ACTION | 流式场景下，用户看到 Agent 想了 5 秒才开始动，体验差 |

**更优雅的实现：ReAct 模式（Reasoning + Acting 融合）**

```
不是: THINKING → ACTION → THINKING → ACTION → DONE
而是: [REASON→ACT→OBSERVE] → [REASON→ACT→OBSERVE] → DONE
        └── 同一个 LLM 调用，思考+行动一体 ──┘
```

**这就是你现在的 AgentRuntime 采用的模式。** 一次 LLM 调用同时产出"思考"（content）和"行动"（tool_calls），两者不分离。具体实现：

```java
// 一次 LLM 调用，一个响应同时包含 content 和 tool_calls
AgentDecision decision = outputParser.parse(response);

// decision 可能是 FinalAnswer（只思考不行动）
// 也可能是 ToolCallsDecision（思考+行动一体）
// 不存在"只思考然后下一轮再行动"的分离
```

**这个设计比 OpenHands 更优雅的地方：**

| 维度 | OpenHands 状态机 | ReAct（你的实现） |
|------|-----------------|-------------------|
| LLM 调用次数 | 每步 2 次 | 每步 1 次 |
| 延迟 | THINKING (2-5s) + ACTION (2-5s) | 1 次调用 (2-5s) |
| 思考质量 | 思考时看不到工具结果 | content 可以基于前一 step 的工具结果推理 |
| 实现复杂度 | 需要状态机框架 | 一个 for 循环 + switch 分支 |
| 可观察性 | 每步状态清晰 | 需要从 ToolCallsDecision.summary 推断思考过程 |
| 模型要求 | 任何模型都能用 | 需要模型支持 Function Calling（content + tool_calls 同时返回）|

**为什么 OpenHands 不直接用 ReAct？**

OpenHands 的原始目标更偏学术——追求**完全可审计、可序列化、可中断恢复的 Agent 状态**。"先想再做"的分离让每一步的输入输出完全确定，方便复现和调试。

**我的结论：你的 ReAct 实现是正确的路径选择。** 对于生产级 Agent，减少 LLM 调用次数（延迟和成本）的收益远超状态机带来的清晰性。如果需要审计能力，可以在 `ToolCallsDecision` 中增强 `reasoningSummary` 而非引入额外的 LLM 调用——让一次调用产出更丰富的 Trace，而非拆成两次调用。
