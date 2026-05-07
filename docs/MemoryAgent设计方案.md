# Memory Agent - Java 记忆系统模块实现计划

## Context

基于 Hermes Agent 记忆系统的设计理念，构建一个可复用的 Java JAR 包——记忆系统模块。核心目标是：**支持多用户多会话的多轮对话，通过 LLM API 从对话中理解用户真正意图，输出结构化的意图总结**。

需求确认：
- 多用户 + 多会话（按 userId 隔离，每个用户可有多个独立会话）
- LLM API 驱动意图总结（如 OpenAI/Claude）
- 可插拔存储（接口 + 默认内存实现，用户可对接 MySQL/Redis 等）
- 纯 Java SDK + Spring Boot Starter 两种集成方式

---

## 1. Maven 模块结构

```
memory-agent/
├── pom.xml                              (父 POM，依赖管理)
├── memory-agent-core/                   (纯 Java 11+ SDK，零框架依赖)
│   └── src/main/java/com/hepu/memory/
│       ├── model/                       (数据模型)
│       ├── spi/                         (服务提供者接口)
│       │   └── defaults/               (默认实现：内存存储、OpenAI总结器)
│       ├── security/                    (安全扫描)
│       ├── conversation/               (会话管理)
│       ├── memory/                      (策划记忆管理)
│       ├── summary/                     (意图总结引擎)
│       ├── compression/                (上下文压缩)
│       ├── config/                      (配置)
│       ├── exception/                  (自定义异常)
│       └── MemoryAgent.java            (主门面类)
├── memory-agent-spring-boot-starter/   (Spring Boot 自动装配)
│   └── src/main/java/com/hepu/memory/spring/
│       ├── autoconfigure/
│       ├── properties/
│       └── condition/
└── memory-agent-examples/              (使用示例)
```

---

## 2. 核心类设计

### 2.1 数据模型 (`model/`)

| 类名 | 说明 |
|------|------|
| `MemoryEntry` | 单条记忆条目（不可变值对象，含 id/content/type/createdAt） |
| `MemoryType` | 枚举：`MEMORY`(Agent笔记)、`USER_PROFILE`(用户画像) |
| `ConversationMessage` | 单条对话消息（含 id/sessionId/role/content/timestamp/metadata） |
| `MessageRole` | 枚举：`USER`、`ASSISTANT`、`SYSTEM`、`TOOL` |
| `Session` | 会话实体（含 userId/source/status/startedAt/messageCount） |
| `SessionStatus` | 枚举：`ACTIVE`、`CLOSED`、`COMPRESSED` |
| `UserProfile` | 跨会话用户画像（聚合所有 MemoryEntry） |
| `IntentSummary` | LLM 意图总结结果（coreIntent/keyTopics/actionItems/emotionalTone/fullSummary） |
| `SecurityScanResult` | 安全扫描结果 |
| `MemorySnapshot` | 冻结快照（会话启动时捕获，会话期间不可变） |

### 2.2 SPI 接口 (`spi/`)

**`MemoryStorage`** — 可插拔存储后端
- Session CRUD：createSession / getSession / updateSession / closeSession
- Message CRUD：addMessage / getMessages / getRecentMessages
- Memory CRUD：addMemoryEntry / replaceMemoryEntry / removeMemoryEntry / getMemoryEntries
- UserProfile：getUserProfile
- IntentSummary：saveIntentSummary / getIntentSummaries
- 生命周期：initialize / shutdown

**`IntentSummarizer`** — LLM 集成接口
- `summarize(userId, sessionId, messages, userProfile, previousSummaries)` → IntentSummary
- `summarizeAsync(...)` → CompletableFuture<IntentSummary>
- `compressConversation(messages, targetTokenBudget)` → String

**`MemoryStorageProvider`** — ServiceLoader 工厂接口
- `name()` → "in-memory" / "mysql" 等
- `create(config)` → MemoryStorage
- `priority()` → 优先级

### 2.3 默认实现 (`spi/defaults/`)

**`InMemoryMemoryStorage`** — 基于 ConcurrentHashMap 的线程安全内存存储
- 使用 `compute()` 保证原子 read-modify-write
- 数据 JVM 关闭后丢失

**`OpenAIIntentSummarizer`** — 基于 java.net.http.HttpClient 的 OpenAI 兼容 API 调用
- 零外部 HTTP 依赖
- 支持 OpenAI / Claude / 任何兼容 API

### 2.4 安全扫描 (`security/`)

**`SecurityScanner`** — 三层防护
1. 不可见 Unicode 字符检测（零宽字符、RTL/LTR 覆盖）
2. 提示注入模式检测（"ignore previous instructions" 等）
3. 数据泄露/后门植入检测

### 2.5 会话管理 (`conversation/`)

**`ConversationManager`** — 会话生命周期 + 消息管理
- createSession / closeSession
- addUserMessage / addAssistantMessage / addToolMessage
- getMessages / getRecentMessages / searchMessages

### 2.6 策划记忆管理 (`memory/`)

**`CuratedMemoryManager`** — 用户画像 + Agent 笔记
- `captureSnapshot(userId)` — 会话启动时冻结快照（类比 Hermes 冻结机制）
- `addEntry / replaceEntry / removeEntry` — 带安全扫描、去重、容量检查
- 写入立即持久化但不影响当前会话快照 → 保护 LLM Prefix Cache

### 2.7 意图总结引擎 (`summary/`)

**`IntentSummarizationEngine`** — 编排何时总结、如何总结
- 触发条件（可配置）：消息数达到阈值 / Token 数达到阈值 / 会话关闭 / 显式请求
- 执行流程：加载消息 → 加载用户画像 → 构造 Prompt → 调用 LLM → 持久化结果

**`PromptConstructor`** — LLM Prompt 构造（内部类）
- System 指令（角色定义 + 输出格式 JSON）
- 用户画像上下文
- 前序摘要（延续性）
- 对话记录（格式化 transcript）

### 2.8 上下文压缩 (`compression/`)

**`ContextCompressor`** — 三阶段压缩管线
1. 工具输出裁剪（纯逻辑，无 LLM 调用）
2. 头部 + 尾部保护
3. 中间部分 LLM 摘要

### 2.9 主门面 (`MemoryAgent.java`)

Builder 模式构建，聚合所有子系统：
```java
MemoryAgent agent = MemoryAgent.builder()
    .config(MemoryAgentConfig.builder()
        .storageType("in-memory")
        .memoryCharLimit(2200)
        .summarizationMessageThreshold(10)
        .build())
    .build();

Session session = agent.createSession("user-123", "api");
agent.addUserMessage(session.getId(), "我想构建一个订单管理API");
agent.addAssistantMessage(session.getId(), "好的，我来帮你...");
IntentSummary summary = agent.summarize(session.getId());
// summary.getCoreIntent() → 用户意图总结
```

---

## 3. Spring Boot Starter 设计

**配置属性** (`memory-agent.*`)：
```yaml
memory-agent:
  enabled: true
  storage-type: in-memory
  memory-char-limit: 2200
  user-profile-char-limit: 1375
  summarization-message-threshold: 10
  summarize-on-session-close: true
  security-scan-enabled: true
  llm:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
    base-url: https://api.openai.com/v1
    max-tokens: 2000
    temperature: 0.3
```

**自动装配**：
- `MemoryAgentAutoConfiguration` — @ConditionalOnMissingBean 条件注入
- 支持：自定义 MemoryStorage Bean、自定义 IntentSummarizer Bean
- 默认：InMemory 存储 + OpenAI 总结器（配置了 api-key 时）

---

## 4. 核心数据流

### 意图总结流程
```
summarize(sessionId) 被调用
  → 加载该会话所有消息
  → 加载用户画像（跨会话记忆）
  → 加载前序摘要（如有）
  → PromptConstructor 构造 Prompt
  → IntentSummarizer SPI 调用 LLM API
  → 解析 JSON 响应为 IntentSummary
  → 持久化到存储
  → 返回 IntentSummary
```

### 记忆写入流程
```
addMemory(userId, content, type)
  → SecurityScanner 扫描内容
  → 原子操作：加锁 → 重新加载最新数据 → 去重 → 容量检查 → 写入 → 解锁
  → 立即持久化，但当前会话快照不变（Prefix Cache 安全）
```

---

## 5. LLM Prompt 设计

意图总结 Prompt 包含四个部分：
1. **System 指令**：角色定义 + 分析框架（5个维度）+ JSON 输出格式
2. **用户画像上下文**：从 CuratedMemoryManager 获取的已知信息
3. **前序摘要**：同一会话中之前的总结结果（保证连续性）
4. **对话记录**：格式化的 `[User]: ... [Assistant]: ...` transcript

输出结构化 JSON：
```json
{
  "coreIntent": "一句话核心意图",
  "keyTopics": ["主题1", "主题2"],
  "actionItems": ["待办1", "待办2"],
  "emotionalTone": "neutral",
  "fullSummary": "3-5句详细叙述"
}
```

---

## 6. 关键设计决策

| 决策 | 原因 |
|------|------|
| 字符数上限（非 Token 数） | 跨模型一致性，Token 计数模型依赖（GPT vs Claude 对中文差异大） |
| 冻结快照机制 | 保护 LLM API Prefix Cache，会话期间 system prompt 不变 |
| SPI + ServiceLoader | 核心模块零框架依赖，标准 Java 机制 |
| ConcurrentHashMap.compute() | 默认内存存储的原子 read-modify-write |
| PromptConstructor 为内部类 | Prompt 格式是实现细节，用户通过 IntentSummarizer SPI 自定义 |

---

## 7. 实现顺序

| 步骤 | 模块 | 说明 |
|------|------|------|
| 1 | `model/` + `exception/` | 所有数据模型和异常类 |
| 2 | `config/` | MemoryAgentConfig 配置类 |
| 3 | `spi/` | MemoryStorage / IntentSummarizer 接口定义 |
| 4 | `security/` | SecurityScanner 安全扫描器 |
| 5 | `spi/defaults/` | InMemoryMemoryStorage 默认实现 |
| 6 | `conversation/` | ConversationManager 会话管理 |
| 7 | `memory/` | CuratedMemoryManager 策划记忆 |
| 8 | `summary/` | PromptConstructor + IntentSummarizationEngine |
| 9 | `spi/defaults/` | OpenAIIntentSummarizer 默认 LLM 实现 |
| 10 | `compression/` | ContextCompressor 上下文压缩 |
| 11 | `MemoryAgent.java` | 主门面 + Builder |
| 12 | `spring-boot-starter/` | Spring Boot 自动装配模块 |
| 13 | `examples/` | 使用示例 |
| 14 | 单元测试 | 贯穿每个模块 |

---

## 8. 技术选型

- Java 11+（使用 java.net.http.HttpClient，零外部 HTTP 依赖）
- Jackson（JSON 序列化，optional 依赖，仅默认 LLM 实现需要）
- SLF4J（日志门面，无实现，用户自选）
- JUnit 5（测试）
- Spring Boot 3.x（Starter 模块，optional 依赖）
- Maven（构建工具）

---

## 9. 验证方式

1. **单元测试**：每个模块独立测试（mock SPI 接口）
2. **集成测试**：InMemory 存储 + Mock LLM → 端到端多轮对话 → 意图总结
3. **示例程序**：
   - `PureJavaExample`：纯 Java SDK 使用演示
   - `SpringBootExample`：Spring Boot Starter yaml 配置演示
4. **构建验证**：`mvn clean install` 三个模块全部通过
