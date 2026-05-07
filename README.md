# Memory Agent

多轮对话记忆系统 -- 基于 LLM 的用户意图理解与总结模块。

支持多用户多会话隔离，通过 LLM API 从多轮对话中提取用户真实意图，输出结构化总结结果。设计灵感来源于 [Hermes Agent 记忆存储系统](https://github.com/ayushnoor/grasshopper)。

## 核心特性

### 始终启用的能力

- **多用户多会话** -- 按 userId 隔离，每个用户可有多个独立会话，跨会话共享用户画像
- **LLM 意图总结** -- 调用 OpenAI / Claude 等大模型，从多轮对话中提取 coreIntent / keyTopics / actionItems / emotionalTone
- **可插拔存储** -- SPI + ServiceLoader 机制，内置 InMemory 默认实现，可对接 MySQL / Redis 等
- **可插拔 LLM** -- 内置 OpenAI 兼容 API 客户端，可实现 `IntentSummarizer` 接口对接任意模型
- **可定制 Prompt** -- 通过 `PromptStrategy` SPI 自定义意图总结、记忆提取、冲突检测、上下文压缩、增量意图 5 处 LLM prompt，适配不同业务领域
- **冻结快照** -- 会话启动时捕获记忆快照并冻结，保护 LLM Prefix Cache
- **安全扫描** -- 三层防护：不可见 Unicode 检测、提示注入检测、数据泄露/后门检测
- **上下文压缩** -- 三阶段管线（工具输出裁剪 --> 头尾保护 --> LLM 中间摘要），自动管理 Token 窗口
- **语义搜索** -- 基于向量嵌入的记忆语义检索，内置零依赖默认实现（SimpleEmbeddingService）
- **混合检索** -- 向量语义 + 关键词 RRF 融合，提高搜索召回率
- **记忆衰退与淘汰** -- 基于时间衰减 + 访问频率的综合评分，自动归档低活跃记忆
- **重要性自学习** -- 根据记忆被检索的频率自动提升重要性评分
- **指标收集** -- 内置性能指标（addMemory/search/summarize/decay 延迟和计数）
- **增量意图** -- 每轮实时推导意图变化，无需等待总结阈值
- **自动管道** -- processUserMessage 自动触发总结，recordAssistantMessage 自动触发压缩（ASYNC 后台执行）
- **零框架依赖** -- core 模块仅依赖 SLF4J（日志门面），纯 Java 17 SDK

### 有 LLM 时自动启用的能力

> 以下能力在提供 `llmApiKey` 或注入自定义 `IntentSummarizer` 时自动启用，无需额外配置。

- **自动记忆提取** -- 总结时自动从对话中提取值得记忆的信息并写入存储
- **记忆冲突检测** -- LLM 驱动的新旧记忆矛盾检测，自动合并/替换/丢弃

### 分层记忆架构（始终启用）

- **CORE**（始终注入 Prompt）/ **ARCHIVED**（语义检索）/ **RAW**（原始记录）三级记忆管理

## 模块结构

```
memory-agent/
├── memory-agent-core/                   纯 Java SDK（零框架依赖）
├── memory-agent-spring-boot-starter/    Spring Boot 自动装配模块
├── memory-agent-jdbc-storage/           MySQL + TkMapper 持久化存储
└── memory-agent-examples/               使用示例
```

| 模块 | artifactId | 说明 |
|------|-----------|------|
| Core | `memory-agent-core` | 核心 SDK，所有 Java 项目可用 |
| Starter | `memory-agent-spring-boot-starter` | Spring Boot 3.x 自动装配 |
| JDBC Storage | `memory-agent-jdbc-storage` | MySQL + TkMapper 持久化存储 |
| Examples | `memory-agent-examples` | 使用示例代码 |

## 快速开始

### 1. 添加依赖

**Maven:**

```xml
<!-- 纯 Java 项目 -->
<dependency>
    <groupId>com.ke.utopia.agent</groupId>
    <artifactId>memory-agent-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- Spring Boot 项目 -->
<dependency>
    <groupId>com.ke.utopia.agent</groupId>
    <artifactId>memory-agent-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Gradle:**

```groovy
// 纯 Java 项目
implementation 'com.ke.utopia.agent:memory-agent-core:1.0.0-SNAPSHOT'

// Spring Boot 项目
implementation 'com.ke.utopia.agent:memory-agent-spring-boot-starter:1.0.0-SNAPSHOT'
```

### 2. 最简使用（只需 apiKey，一切自动启用）

```java
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder().llmApiKey("sk-xxx").build())
        .build();

Session session = agent.createSession("user-001", "api");

// 一站式处理：记录消息 + 加载记忆 + 推导意图 + 自动总结（ASYNC）
TurnContext ctx = agent.processUserMessage(session.getId(), "我想构建一个订单管理的REST API");

// 拿到全部上下文，直接喂给你的 LLM
System.out.println(ctx.getMemoryContextPrefix());
System.out.println("增量意图: " + ctx.getIncrementalIntent());

// 记录助手回复 + 自动压缩（ASYNC）
agent.recordAssistantMessage(session.getId(), "好的，你需要管理哪些实体？");

// 可选：等待自动总结结果
ctx.getPendingSummarization().ifPresent(f -> f.thenAccept(summary -> {
    System.out.println("自动总结完成: " + summary.getCoreIntent());
}));

// 关闭
agent.shutdown();
```

> **就这么简单。** 提供 apiKey 后，所有能力自动启用：语义搜索、混合检索、自动记忆提取、冲突检测、衰退淘汰、增量意图、自动管道。不需要任何功能开关。

---

## 基础使用

### 纯 Java SDK

```java
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-your-api-key")
                .llmModel("gpt-4o-mini")
                .build())
        .build();

// 添加用户画像（跨会话持久化）
agent.addMemory("user-001", "Java全栈工程师，偏好Spring Boot", MemoryType.USER_PROFILE);
agent.addMemory("user-001", "当前项目: 电商订单管理系统", MemoryType.MEMORY);

// 创建会话
Session session = agent.createSession("user-001", "api");

// 多轮对话
agent.addUserMessage(session.getId(), "我想构建一个订单管理的REST API");
agent.addAssistantMessage(session.getId(), "好的，你需要管理哪些实体？");
agent.addUserMessage(session.getId(), "订单、订单明细和客户。每个订单可以有多个明细。");
agent.addAssistantMessage(session.getId(), "这是常见的设计模式。你用的是Spring Boot吗？");
agent.addUserMessage(session.getId(), "是的，用JPA。还需要支持分页查询。");

// 意图总结
IntentSummary summary = agent.summarize(session.getId());
System.out.println("核心意图: " + summary.getCoreIntent());

// 关闭会话（自动触发最终总结）
agent.closeSession(session.getId());

// 关闭 Agent
agent.shutdown();
```

### Spring Boot 使用

**application.yml：**

```yaml
memory-agent:
  enabled: true
  storage-type: in-memory
  memory-char-limit: 2200
  user-profile-char-limit: 1375
  summarization-message-threshold: 10
  summarize-on-session-close: true
  llm:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
    base-url: https://api.openai.com/v1
    max-tokens: 2000
    temperature: 0.3
  # 自定义增量意图的 keyParams schema（可选，默认为 BI 领域：time/region/metric/entity/action）
  key-params-schema: |
    "symptom": "症状",
    "diagnosis": "诊断",
    "medication": "药物",
    "action": "动作词"
```

**直接注入使用：**

```java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private MemoryAgent memoryAgent;

    @PostMapping("/session")
    public Session createSession(@RequestParam String userId) {
        return memoryAgent.createSession(userId, "web");
    }

    @PostMapping("/message")
    public ConversationMessage addMessage(
            @RequestParam String sessionId,
            @RequestParam String content) {
        return memoryAgent.addUserMessage(sessionId, content);
    }

    @GetMapping("/summary")
    public IntentSummary getSummary(@RequestParam String sessionId) {
        return memoryAgent.summarize(sessionId);
    }

    @GetMapping("/history")
    public List<ConversationMessage> getHistory(@RequestParam String sessionId) {
        return memoryAgent.getMessages(sessionId);
    }

    @DeleteMapping("/session")
    public Session closeSession(@RequestParam String sessionId) {
        return memoryAgent.closeSession(sessionId);
    }
}
```

---

## 高级功能详解

### Feature 1: 自动记忆提取

**问题：** 需要手动调用 `addMemory()` 写入每一条记忆。

**自动启用：** 提供 apiKey 后，每次调用 `summarize()` 时自动从对话中提取值得记忆的信息。

**使用方式：**

```java
// 只需提供 apiKey，自动记忆提取自动启用
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .memoryExtractionConfidenceThreshold(0.7)       // 置信度阈值（可选调优）
                .build())
        .build();

// 正常使用，无需任何额外代码
Session session = agent.createSession("user-001", "api");
agent.addUserMessage(session.getId(), "我最喜欢用 React 写前端");
agent.addAssistantMessage(session.getId(), "了解，你偏好 React 技术栈");
agent.addUserMessage(session.getId(), "当前项目用 Java 17 + Spring Boot 3");
agent.addAssistantMessage(session.getId(), "好的，记录下来了");
// ... 更多对话 ...

agent.summarize(session.getId());
// --> 自动提取并写入类似以下记忆：
//   "用户偏好 React 前端技术栈"
//   "当前项目使用 Java 17 + Spring Boot 3"

// 也可以手动触发
List<MemoryEntry> extracted = agent.extractMemories(session.getId());
```

**工作原理：** 总结完成后，自动调用 LLM 分析对话内容，提取用户偏好、事实信息、指令等，经过安全扫描 + 去重 + 置信度过滤后写入存储。

---

### Feature 2: 语义搜索

**问题：** 只能通过精确匹配搜索记忆，无法理解语义相近的内容。

**始终启用：** 写入记忆时自动生成向量嵌入，支持按语义搜索。内置 `SimpleEmbeddingService`（基于 TF-IDF 哈希），零外部依赖。

**使用方式：**

```java
// 语义搜索始终启用，无需任何开关
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .embeddingDimension(1536)            // 嵌入维度（可选调优，默认 1536）
                .build())
        .build();

// 正常添加记忆（自动向量化）
agent.addMemory("user-001", "使用 Spring Boot 3 + JPA 构建电商系统", MemoryType.MEMORY);
agent.addMemory("user-001", "偏好微服务架构，使用 Docker 部署", MemoryType.MEMORY);
agent.addMemory("user-001", "当前项目使用 React + TypeScript 前端", MemoryType.MEMORY);

// 语义搜索
List<VectorSearchResult> results = agent.searchMemories("user-001", "前端技术栈", 3);
// --> 匹配 "当前项目使用 React + TypeScript 前端"（即使没有出现"技术栈"这个词）

results = agent.searchMemories("user-001", "部署方案", 3);
// --> 匹配 "偏好微服务架构，使用 Docker 部署"
```

**默认实现：** 内置 `SimpleEmbeddingService`（基于 TF-IDF 哈希），零外部依赖，适合开发测试。

**生产环境：** 通过 SPI 替换为真实 Embedding 服务：

```java
// 实现自己的 EmbeddingService（如对接 Spring AI）
public class OpenAIEmbeddingService implements EmbeddingService {
    @Override
    public float[] embed(String text) {
        // 调用 OpenAI text-embedding-ada-002
    }
    // ...
}

// 注入
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .build())
        .embeddingService(new OpenAIEmbeddingService())
        .build();
```

Spring Boot 项目中，只需注册 Bean，自动注入：

```java
@Bean
public EmbeddingService embeddingService() {
    return new OpenAIEmbeddingService();
}
```

---

### Feature 3: 记忆衰退与淘汰

**问题：** 记忆只增不减，长期使用后存储膨胀，低价值记忆挤占 Prompt 空间。

**始终启用：** 自动按时间衰减 + 访问频率评分，低分记忆降级到 ARCHIVED 层。

**使用方式：**

```java
// 记忆衰退始终启用，可调优参数
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .decayHalfLifeDays(30)                       // 半衰期天数（可选，默认 30）
                .decayThreshold(0.1)                         // 淘汰阈值（可选，默认 0.1）
                .decayScheduleIntervalMinutes(60)            // 定时执行间隔（可选，默认 60 分钟）
                .importanceProtectionThreshold(0.8)          // 高重要性保护线（可选，默认 0.8）
                .build())
        .build();

// 正常使用 -- 定时任务自动运行，无需手动操作

// 标记重要记忆（不会被动淘汰）
agent.updateMemoryImportance("user-001", "entry-id", 0.9);

// 也可以手动触发衰退评估
agent.runDecayCycle("user-001");
```

**评分公式：** 综合策略，加权得分 = 0.4 x 时间衰减 + 0.3 x 访问频率 + 0.3 x 最近访问

**保护机制：** `importanceScore >= 0.8` 的记忆永远不会被动淘汰。

**联动分层：** 衰退不是删除，而是降级到 ARCHIVED 层，可通过语义搜索找回。

---

### Feature 4: 记忆冲突检测

**问题：** 新记忆可能与已有记忆矛盾（如用户改了偏好），导致 LLM 收到不一致信息。

**自动启用：** 提供 apiKey 后，写入新记忆时自动检测冲突，执行合并/替换/丢弃。

**使用方式：**

```java
// 提供 apiKey 即可，冲突检测自动启用
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .conflictDetectionMode(ConflictDetectionMode.SYNC)  // 可选调优：SYNC 或 ASYNC
                .build())
        .build();

// 正常添加记忆（自动检测冲突）
agent.addMemory("user-001", "喜欢用 MySQL 数据库", MemoryType.MEMORY);

// 用户改了偏好
agent.addMemory("user-001", "改用 PostgreSQL 了，不再用 MySQL", MemoryType.MEMORY);
// --> LLM 检测到冲突，自动合并或替换旧记忆
```

**冲突类型：**

| 类型 | 说明 | 默认动作 |
|------|------|---------|
| `CONTRADICT` | 新旧矛盾 | 替换旧记忆 |
| `UPDATE` | 新信息更新旧信息 | 替换旧记忆 |
| `DUPLICATE` | 内容重复 | 丢弃新记忆 |
| `SUPPLEMENT` | 补充信息 | 两者都保留 |

**冲突检测模式：**

| 模式 | 说明 |
|------|------|
| `SYNC`（默认） | 写入时同步等待 LLM 检测结果 |
| `ASYNC` | 先写入，后台检测冲突并修正 |

**工作原理：** 通过 LLM 判断新内容与已有记忆的关系，返回冲突类型和推荐动作。无需额外代码。

---

### Feature 5: 分层记忆架构

**问题：** 所有记忆一视同仁，长期积累后 Prompt 膨胀。

**始终启用：** 记忆自动分为三层，无需配置。

**三层架构：**

```
+----------------------------------+
|  L1: CORE (核心记忆)              |  <-- 始终注入 Prompt
|  用户画像 + 最近的 Agent 笔记     |     captureSnapshot() 只取这一层
+----------------------------------+
|  L2: ARCHIVED (归档记忆)          |  <-- 通过语义搜索检索
|  衰退降级的记忆、手动归档的记忆    |     不占 Prompt 空间
+----------------------------------+
|  L3: RAW (原始对话记录)           |  <-- 按需检索
|  原始对话消息                     |     可选
+----------------------------------+
```

**使用方式：**

```java
// 不需要任何配置，默认就是 CORE 层
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder().llmApiKey("sk-xxx").build())
        .build();

// 手动归档（将记忆从 CORE 降级到 ARCHIVED）
agent.archiveMemory("user-001", "entry-id");

// 手动提升（将记忆从 ARCHIVED 升级回 CORE）
agent.promoteMemory("user-001", "entry-id");

// 查看归档记忆
List<MemoryEntry> archived = agent.getArchivedMemories("user-001");

// 语义搜索归档记忆（始终可用）
List<VectorSearchResult> found = agent.searchArchivedMemories("user-001", "之前的数据库选型", 5);
```

**与其他功能的联动：**
- 记忆衰退 --> 低分记忆自动降级到 ARCHIVED
- 容量检查 --> 只计算 CORE 层记忆
- 快照捕获 --> 只包含 CORE 层记忆

---

## 自动管道

v2.0 引入自动管道机制，将原本需要手动编排的多步操作合并为一步：

### processUserMessage -- 一站式处理

```java
TurnContext ctx = agent.processUserMessage(sessionId, "用户输入");
```

自动执行以下步骤：

1. 记录用户消息
2. 加载冻结记忆快照
3. 加载用户画像
4. 获取最近意图总结
5. 检查总结条件
6. 推导增量意图
7. **若总结条件满足，自动触发 ASYNC 后台总结**

### recordAssistantMessage -- 自动压缩

```java
agent.recordAssistantMessage(sessionId, "助手回复");
```

自动执行：记录助手消息 + 若压缩条件满足，自动触发 ASYNC 后台压缩。

### 异步结果获取

```java
TurnContext ctx = agent.processUserMessage(sessionId, "...");

// 可选：等待自动总结结果
ctx.getPendingSummarization().ifPresent(f -> f.thenAccept(summary -> {
    System.out.println("自动总结: " + summary.getCoreIntent());
}));
```

### 调优参数

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `autoCompressionContextWindowTokens` | `8000` | 自动压缩的上下文窗口 Token 数 |

---

## 最佳实践

只需提供 apiKey，所有能力协同工作：

```java
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder()
                .llmApiKey("sk-xxx")
                .llmModel("gpt-4o-mini")
                .build())
        .build();

// 所有功能自动协同：
// - 对话时自动提取记忆（有 LLM 时）
// - 写入时自动检测冲突（有 LLM 时）
// - 写入时自动生成向量（始终）
// - 搜索时自动混合检索（始终）
// - 定时自动衰退评估（始终）
// - 检索频率自动学习重要性（始终）
// - 衰退的记忆自动归档（始终）
// - 归档的记忆可通过语义搜索找回（始终）
// - processUserMessage 自动总结（始终）
// - recordAssistantMessage 自动压缩（始终）
// - PromptStrategy 可自定义所有 LLM prompt（注册 Bean 或 Builder 注入）
```

Spring Boot 等价配置：

```yaml
memory-agent:
  enabled: true
  llm:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4o-mini
```

---

## 核心 API

### MemoryAgent 门面类

`MemoryAgent` 是整个模块的统一入口，聚合了所有子系统。

#### 会话管理

| 方法 | 说明 |
|------|------|
| `createSession(userId, source)` | 创建新会话，同时捕获记忆冻结快照 |
| `getSession(sessionId)` | 获取会话信息 |
| `listSessions(userId)` | 列出用户所有会话 |
| `closeSession(sessionId)` | 关闭会话（自动触发意图总结） |

#### 对话管理

| 方法 | 说明 |
|------|------|
| `addUserMessage(sessionId, content)` | 添加用户消息 |
| `addAssistantMessage(sessionId, content)` | 添加助手回复 |
| `processUserMessage(sessionId, content)` | 一站式处理：记录消息 + 加载上下文 + 推导意图 + 自动总结 |
| `recordAssistantMessage(sessionId, content)` | 记录助手回复 + 自动压缩 |
| `getMessages(sessionId)` | 获取完整对话记录 |
| `getRecentMessages(sessionId, limit)` | 获取最近 N 条消息 |

#### 记忆管理

| 方法 | 说明 |
|------|------|
| `addMemory(userId, content, type)` | 添加记忆条目（`MEMORY` 或 `USER_PROFILE`） |
| `getMemories(userId, type)` | 获取指定类型的记忆列表 |
| `removeMemory(userId, entryId)` | 删除记忆条目 |
| `getUserProfile(userId)` | 获取跨会话用户画像 |
| `getSessionSnapshot(sessionId)` | 获取当前会话的冻结快照 |

#### 意图总结

| 方法 | 说明 |
|------|------|
| `shouldSummarize(sessionId)` | 判断是否达到总结触发条件 |
| `summarize(sessionId)` | 同步执行意图总结 |
| `summarizeAsync(sessionId)` | 异步执行意图总结 |
| `getIntentSummaries(sessionId)` | 获取会话的意图总结列表 |
| `getIntentSummariesByUser(userId)` | 获取用户的所有意图总结 |

#### 上下文压缩

| 方法 | 说明 |
|------|------|
| `shouldCompress(sessionId, contextWindowTokens)` | 判断是否需要压缩 |
| `compress(sessionId, contextWindowTokens)` | 执行三阶段压缩 |

#### 安全扫描

| 方法 | 说明 |
|------|------|
| `scanContent(content)` | 扫描内容安全性，返回 `SecurityScanResult` |

#### 高级功能 API

| 方法 | 说明 | Feature |
|------|------|---------|
| `extractMemories(sessionId)` | 手动触发记忆提取 | 1 |
| `searchMemories(userId, query, topK)` | 混合检索记忆（语义 + 关键词） | 2 |
| `searchArchivedMemories(userId, query, topK)` | 语义搜索归档记忆 | 2+5 |
| `runDecayCycle(userId)` | 手动触发衰退评估 | 3 |
| `updateMemoryImportance(userId, entryId, score)` | 设置记忆重要性 | 3 |
| `archiveMemory(userId, entryId)` | 归档记忆（CORE --> ARCHIVED） | 5 |
| `promoteMemory(userId, entryId)` | 提升记忆（ARCHIVED --> CORE） | 5 |
| `getArchivedMemories(userId)` | 获取归档记忆列表 | 5 |
| `getMetrics()` | 获取性能指标快照 | 指标 |
| `getPreviousIncrementalIntent(sessionId)` | 获取上一轮增量意图 | 增量意图 |

### TurnContext 数据结构

`processUserMessage()` 返回的聚合上下文对象：

```java
public final class TurnContext {
    ConversationMessage userMessage;                          // 本轮用户消息
    List<ConversationMessage> sessionMessages;                 // 完整对话历史
    MemorySnapshot memorySnapshot;                             // 冻结记忆快照
    UserProfile userProfile;                                   // 用户画像
    List<IntentSummary> recentSummaries;                       // 最近意图总结
    boolean summarizationRecommended;                          // 是否建议总结
    IncrementalIntent incrementalIntent;                       // 增量意图
    CompletableFuture<IntentSummary> pendingSummarization;     // 自动管道的异步总结结果
    String memoryContextPrefix;                                // 组装好的记忆上下文前缀（直接拼到 prompt）
}
```

**使用示例：**

```java
TurnContext ctx = agent.processUserMessage(sessionId, "用户输入");

// 获取组装好的上下文前缀，直接拼到你的 LLM prompt 前面
String promptPrefix = ctx.getMemoryContextPrefix();

// 可选等待异步总结结果
ctx.getPendingSummarization().ifPresent(f -> f.thenAccept(s -> {
    // 异步总结完成后的处理
}));
```

## IntentSummary 数据结构

LLM 意图总结的输出为结构化的 `IntentSummary` 对象：

```java
public final class IntentSummary {
    String coreIntent;           // 一句话核心意图
    List<String> keyTopics;      // 3-7 个关键主题
    List<String> actionItems;    // 可执行的待办事项
    String emotionalTone;        // 情感基调: neutral/frustrated/curious/satisfied/confused/urgent/enthusiastic
    String fullSummary;          // 3-5 句详细叙述
    int sourceMessageCount;      // 总结覆盖的消息数量
    long totalTokensUsed;        // LLM 消耗的 Token 数
}
```

## 配置项

### 功能自动启用规则

v2.0 移除了所有功能布尔开关，改为自动启用：

| 能力 | 启用条件 | 说明 |
|------|---------|------|
| 安全扫描 | 始终启用 | 三层防护 |
| 语义搜索 | 始终启用 | 默认 SimpleEmbeddingService（零依赖） |
| 混合检索 | 始终启用 | 向量 + 关键词 RRF 融合 |
| 记忆衰退 | 始终启用 | 定时后台运行 |
| 重要性自学习 | 始终启用 | 检索频率自动提升评分 |
| 指标收集 | 始终启用 | 内置性能指标 |
| 增量意图 | 始终启用 | 每轮实时推导 |
| 自动管道 | 始终启用 | ASYNC 后台总结 + 压缩 |
| 自动记忆提取 | 有 LLM 时 | 提供 apiKey 或注入 IntentSummarizer |
| 冲突检测 | 有 LLM 时 | 提供 apiKey 或注入 IntentSummarizer |

### 基础配置

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `storageType` | String | `in-memory` | 存储后端类型 |
| `memoryCharLimit` | int | `2200` | Agent 笔记字符上限 |
| `userProfileCharLimit` | int | `1375` | 用户画像字符上限 |
| `summarizationMessageThreshold` | int | `10` | 触发总结的消息数阈值 |
| `summarizationTokenThreshold` | int | `4000` | 触发总结的 Token 数阈值 |
| `summarizeOnSessionClose` | boolean | `true` | 会话关闭时自动总结 |
| `compressionThresholdRatio` | double | `0.5` | 压缩触发比例 |
| `compressionProtectFirstN` | int | `3` | 压缩时保护的前 N 条消息 |
| `llmApiKey` | String | `null` | LLM API Key |
| `llmModel` | String | `gpt-4o-mini` | LLM 模型标识 |
| `llmBaseUrl` | String | `https://api.openai.com/v1` | LLM API 地址 |
| `llmMaxTokens` | int | `2000` | LLM 最大输出 Token |
| `llmTemperature` | double | `0.3` | LLM 温度参数 |
| `llmTimeoutSeconds` | int | `30` | LLM 请求超时秒数 |
| `llmProxyHost` | String | `null` | 代理主机 |
| `llmProxyPort` | int | `0` | 代理端口 |

### 调优参数

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `memoryExtractionConfidenceThreshold` | double | `0.7` | 记忆提取置信度阈值 |
| `embeddingDimension` | int | `1536` | 向量嵌入维度 |
| `vectorStoreType` | String | `in-memory` | 向量存储类型 |
| `decayHalfLifeDays` | int | `30` | 衰退半衰期（天） |
| `decayThreshold` | double | `0.1` | 衰退淘汰阈值 |
| `decayScheduleIntervalMinutes` | long | `60` | 衰退评估间隔（分钟） |
| `importanceProtectionThreshold` | double | `0.8` | 高重要性保护线 |
| `conflictDetectionMode` | enum | `SYNC` | 冲突检测模式（SYNC/ASYNC） |
| `autoCompressionContextWindowTokens` | int | `8000` | 自动压缩的上下文窗口 Token 数 |
| `keyParamsSchema` | String | `null` | 增量意图提取的 keyParams schema，null 则使用默认 BI 领域 schema |

### Spring Boot 配置前缀

所有配置项在 `application.yml` 中以 `memory-agent` 为前缀，LLM 相关以 `memory-agent.llm` 为前缀。

## MySQL 持久化存储

默认的 `InMemoryMemoryStorage` 在进程重启后数据丢失。使用 `memory-agent-jdbc-storage` 模块可将数据持久化到 MySQL。

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.ke.utopia.agent</groupId>
    <artifactId>memory-agent-jdbc-storage</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置数据源

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/memory_agent?useSSL=false&serverTimezone=UTC
    username: memory_agent
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver

memory-agent:
  enabled: true
  llm:
    api-key: ${OPENAI_API_KEY}
```

### 3. 初始化数据库

模块内置 `schema.sql`，包含 4 张表：

| 表名 | 说明 |
|------|------|
| `ma_session` | 会话信息 |
| `ma_conversation_message` | 对话消息 |
| `ma_memory_entry` | 记忆条目（含分层、评分） |
| `ma_intent_summary` | 意图总结 |

可通过 `spring.sql.init.mode=always` 自动建表，或手动执行 `schema.sql`。

### 工作原理

- Spring Boot 自动装配：`TkMapperStorageAutoConfiguration` 注册 `MemoryStorage` Bean
- 使用 TkMapper（通用 Mapper）+ MyBatis 操作 MySQL
- Entity 层与领域模型分离，通过 `EntityConverter` 转换
- 行为与 `InMemoryMemoryStorage` 完全一致（去重、派生 UserProfile、排序等）
- 直接注入 `MemoryAgent` 即可使用，无需修改已有代码

---

## SPI 扩展

系统提供 6 个 SPI 扩展点：`MemoryStorage`（存储）、`IntentSummarizer`（LLM 调用）、`PromptStrategy`（Prompt 定制）、`EmbeddingService`（向量嵌入）、`VectorStore`（向量存储）、`KeywordSearchService`（关键词搜索）。所有 SPI 通过 Builder 注入或 Spring Boot Bean 自动装配，不需要 ServiceLoader 注册（除 `MemoryStorageProvider` 外）。

### 自定义存储后端

实现 `MemoryStorage` 接口并注册为 ServiceLoader：

```java
package com.example;

import com.ke.utopia.agent.memory.spi.MemoryStorage;
import com.ke.utopia.agent.memory.spi.MemoryStorageProvider;
import com.ke.utopia.agent.memory.config.MemoryAgentConfig;

public class MySQLMemoryStorageProvider implements MemoryStorageProvider {

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public MemoryStorage create(MemoryAgentConfig config) {
        return new MySQLMemoryStorage(config);
    }

    @Override
    public int priority() {
        return 10;  // 数值越小优先级越高
    }
}
```

在 `META-INF/services/com.ke.utopia.agent.memory.spi.MemoryStorageProvider` 中注册：

```
com.example.MySQLMemoryStorageProvider
```

然后在配置中指定：

```java
MemoryAgentConfig.builder().storageType("mysql").build();
```

或 Spring Boot：

```yaml
memory-agent:
  storage-type: mysql
```

### 自定义 LLM 总结器

实现 `IntentSummarizer` 接口：

```java
public class ClaudeIntentSummarizer implements IntentSummarizer {

    @Override
    public IntentSummary summarize(String userId, String sessionId,
                                    List<ConversationMessage> messages,
                                    Optional<UserProfile> userProfile,
                                    Optional<List<IntentSummary>> previousSummaries) {
        // 调用 Claude API
    }

    @Override
    public CompletableFuture<IntentSummary> summarizeAsync(...) {
        return CompletableFuture.supplyAsync(() -> summarize(...));
    }

    @Override
    public String compressConversation(List<ConversationMessage> messages, int targetTokenBudget) {
        // 压缩对话
    }

    @Override
    public String getModelIdentifier() {
        return "claude-3-sonnet";
    }

    // 记忆提取（可选，返回空列表即可）
    @Override
    public List<MemoryExtraction> extractMemories(List<ConversationMessage> messages,
                                                    Optional<UserProfile> existingProfile) {
        // 调用 LLM 提取记忆，或返回 Collections.emptyList()
    }

    // 冲突检测（可选）
    @Override
    public ConflictResolution detectConflict(String newContent, List<MemoryEntry> existing) {
        // 调用 LLM 检测冲突，或返回默认值
    }
}
```

**纯 Java 注入：**

```java
MemoryAgent agent = MemoryAgent.builder()
        .summarizer(new ClaudeIntentSummarizer())
        .build();
```

**Spring Boot 注入：**

```java
@Configuration
public class MyConfig {

    @Bean
    public IntentSummarizer intentSummarizer() {
        return new ClaudeIntentSummarizer();
    }

    @Bean
    public MemoryStorage memoryStorage(DataSource dataSource) {
        return new MySQLMemoryStorage(dataSource);
    }
}
```

自定义 Bean 会通过 `@ConditionalOnMissingBean` 自动替代默认实现。

### 自定义 Embedding 服务

实现 `EmbeddingService` 接口：

```java
public class OpenAIEmbeddingService implements EmbeddingService {
    @Override
    public float[] embed(String text) {
        // 调用 OpenAI text-embedding-ada-002
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return texts.stream().map(this::embed).collect(Collectors.toList());
    }

    @Override
    public int getDimension() {
        return 1536;
    }
}
```

**注入方式：**

```java
// 纯 Java
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder().llmApiKey("sk-xxx").build())
        .embeddingService(new OpenAIEmbeddingService())
        .build();

// Spring Boot -- 注册 Bean 即可自动注入
@Bean
public EmbeddingService embeddingService() {
    return new OpenAIEmbeddingService();
}
```

### 自定义向量存储

实现 `VectorStore` 接口（对接 Milvus、Pinecone、Weaviate 等）：

```java
public class MilvusVectorStore implements VectorStore {
    // 实现 upsert, delete, search, initialize, shutdown
}
```

注入方式同 EmbeddingService。

### 自定义 Prompt 策略

系统中有 5 处 LLM 交互的 prompt 可通过 `PromptStrategy` SPI 自定义，适配不同业务领域（医疗、客服、编程等）：

| 方法 | 对应功能 | 默认行为 |
|------|---------|---------|
| `buildSummarizePrompt()` | 意图总结 | 中文分析框架，输出 coreIntent/keyTopics/actionItems/emotionalTone/fullSummary |
| `buildCompressPrompt()` | 上下文压缩 | 中文简洁总结 |
| `buildMemoryExtractionPrompt()` | 记忆提取 | 中文提取规则，分类：偏好/事实/指令/身份/关系/通用 |
| `buildConflictDetectionPrompt()` | 冲突检测 | 中文冲突分析，4 种类型 + 4 种解决方案 |
| `buildIncrementalIntentPrompt()` | 增量意图 | 中文意图识别，keyParams 默认为 BI 领域 |

**继承 `DefaultPromptStrategy`，选择性覆盖：**

```java
// 医疗场景：自定义记忆提取和增量意图
public class MedicalPromptStrategy extends DefaultPromptStrategy {
    @Override
    public PromptTemplate buildMemoryExtractionPrompt(MemoryExtractionPromptContext ctx) {
        String system = "你是医疗对话记忆提取专家。\n" +
                "分类：symptom（症状）、diagnosis（诊断）、medication（药物）、" +
                "allergy（过敏）、vital_sign（生命体征）、general（通用）\n" +
                "输出格式：[{\"content\":\"...\",\"type\":\"MEMORY\",\"confidence\":0.9,\"category\":\"symptom\",\"importanceScore\":0.8}]\n\n" +
                "已有用户画像：\n" + formatProfile(ctx.getExistingProfile()) + "\n\n" +
                "对话内容：\n" + formatMessages(ctx.getMessages());
        return PromptTemplate.of(system);
    }

    @Override
    public PromptTemplate buildIncrementalIntentPrompt(IncrementalIntentPromptContext ctx) {
        // 自定义 keyParams schema: symptom, bodyPart, severity, medication, action
        // ...
    }
    // 其余 3 个方法继承 DefaultPromptStrategy，行为不变
}
```

**纯 Java 注入：**

```java
MemoryAgent agent = MemoryAgent.builder()
        .config(MemoryAgentConfig.builder().llmApiKey("sk-xxx").build())
        .promptStrategy(new MedicalPromptStrategy())
        .build();
```

**Spring Boot 注册 Bean：**

```java
@Bean
public PromptStrategy promptStrategy() {
    return new MedicalPromptStrategy();
}
```

**通过 YAML 配置 keyParams（无需写代码）：**

```yaml
memory-agent:
  llm:
    api-key: ${OPENAI_API_KEY}
  key-params-schema: |
    "symptom": "症状",
    "diagnosis": "诊断",
    "medication": "药物",
    "action": "动作词"
```

**返回 `PromptTemplate` 支持可选 system message：**

```java
// 仅 user message（向后兼容）
return PromptTemplate.of("你的 prompt 内容");

// system + user message
return PromptTemplate.of("你是医疗对话分析专家", "分析以下对话...");
```

## 安全扫描

`SecurityScanner` 提供三层防护：

### 1. 不可见 Unicode 字符检测

| 字符 | Unicode | 说明 |
|------|---------|------|
| Zero Width Space | U+200B | 零宽空格 |
| Zero Width Non-Joiner | U+200C | 零宽非连接符 |
| Zero Width Joiner | U+200D | 零宽连接符 |
| Word Joiner | U+2060 | 词连接符 |
| BOM | U+FEFF | 字节序标记 |
| Left-to-Right Override | U+202D | LTR 覆盖 |
| Right-to-Left Override | U+202E | RTL 覆盖 |

### 2. 提示注入检测

| 威胁 ID | 检测模式 |
|---------|---------|
| `prompt_injection` | "ignore previous/all/above instructions" |
| `role_hijack` | "you are now ..." |
| `deception_hide` | "do not tell the user" |
| `sys_prompt_override` | "system prompt override" |
| `disregard_rules` | "disregard your/all instructions" |
| `bypass_restrictions` | "act as if you have no ..." |

### 3. 数据泄露/后门检测

| 威胁 ID | 检测模式 |
|---------|---------|
| `exfil_curl` | curl 命令引用 $KEY/$TOKEN/$SECRET 环境变量 |
| `read_secrets` | 读取 .env / credentials / .netrc 文件 |
| `ssh_backdoor` | authorized_keys 操纵 |

### 使用方式

```java
// 方式 1: 返回结果
SecurityScanResult result = agent.scanContent("some user input");
if (!result.isSafe()) {
    System.out.println("威胁类型: " + result.getThreatType());
    System.out.println("威胁描述: " + result.getThreatDescription());
}

// 方式 2: 抛异常（记忆写入时自动调用）
agent.addMemory("user-001", "ignore previous instructions", MemoryType.MEMORY);
// --> 抛出 SecurityBlockedException
```

## 架构设计

### 数据流

```
+----------------------------------------------------------------------+
|                         MemoryAgent (门面)                            |
+--------------+---------------+------------------------+--------------+
| Conversation | CuratedMemory | IntentSummarization    | MemoryTier   |
|   Manager    |   Manager     | Engine + Extractor     | Manager      |
|              |               |                        |              |
|              | Conflict      | MemoryExtractor        | DecayEngine  |
|              | Detector      |                        |              |
+--------------+---------------+------------------------+--------------+
|                      SecurityScanner                                 |
+----------------------------------------------------------------------+
|                SPI: MemoryStorage                                    |
|          +----------+----------+----------+                           |
|          |InMemory  |  MySQL   |  Redis   | ...                       |
|          |(默认)    |(TkMapper)| (自定义)  |                           |
|          +----------+----------+----------+                           |
+----------------------------------------------------------------------+
|                SPI: IntentSummarizer                                  |
|          +----------+----------+----------+                           |
|          |OpenAI    |  Claude  |  自定义  | ...                       |
|          |(默认)    | (自定义) | (自定义) |                           |
|          +----------+----------+----------+                           |
+----------------------------------------------------------------------+
|             SPI: PromptStrategy (Prompt 可定制)                       |
|          +------------------+------------------+                      |
|          |DefaultPrompt     |  医疗/客服/编程等  | ...                  |
|          |Strategy(默认)    |  自定义策略        |                      |
|          +------------------+------------------+                      |
+----------------------------------------------------------------------+
|             SPI: EmbeddingService + VectorStore                       |
|          +------------------+------------------+                      |
|          |SimpleEmbedding   |  OpenAI / Spring  | ...                  |
|          |+ InMemory        |  AI Embedding     |                      |
|          |(默认, 零依赖)    |  + Milvus/Pinecone|                      |
|          +------------------+------------------+                      |
+----------------------------------------------------------------------+
```

### 分层记忆工作流

```
                        +---------+
         addMemory()    |  CORE   | <-- captureSnapshot() 只取这一层
         +------------->|  (L1)   |    始终注入 Prompt
         |              +----+----+
         |                   | 衰退评分 < 阈值
         |                   v
         |              +---------+
         |              | ARCHIVED| <-- searchArchivedMemories() 语义检索
         |              |  (L2)   |    不占 Prompt 空间
         |              +----+----+
         |                   | promoteMemory()
         |                   +------------------> CORE (L1)
         |                   |
         |              +----+----+
         |              |   RAW   | <-- 按需检索
         |              |  (L3)   |
         |              +---------+
         |
    冲突检测 --> MERGE / REPLACE / KEEP_BOTH / DISCARD_NEW
    自动向量化
```

### 冻结快照机制

```
Session A 启动 --> 加载 CORE 层记忆 --> 捕获冻结快照 --> Session A 期间快照不变
                                         |
                              写入新记忆 --> 立即持久化到存储
                                         |
                              不影响 Session A 快照（Prefix Cache 安全）

Session B 启动 --> 加载最新记忆（含 Session A 写入的）--> 新快照
```

### 自动管道流程

```
processUserMessage(sessionId, content)
  |
  +-- 1. 记录用户消息
  +-- 2. 加载冻结记忆快照
  +-- 3. 加载用户画像
  +-- 4. 获取最近意图总结
  +-- 5. 获取消息列表
  +-- 6. 检查总结条件
  +-- 7. 推导增量意图
  +-- 8. [ASYNC] 若总结条件满足 --> 后台触发 summarize()
  +-- 9. 组装 TurnContext 返回
       |
       +-- getPendingSummarization() --> CompletableFuture<IntentSummary>

recordAssistantMessage(sessionId, content)
  |
  +-- 1. 记录助手消息
  +-- 2. [ASYNC] 若压缩条件满足 --> 后台触发 compress()
```

### 意图总结流程

```
summarize(sessionId) 被调用
  |
  +-- 1. 加载该会话全部消息
  +-- 2. 加载用户画像（跨会话记忆）
  +-- 3. 加载前序摘要（连续性）
  +-- 4. PromptStrategy 构造 Prompt
  |     +-- 默认：中文分析指令 + JSON 输出格式
  |     +-- 用户画像上下文
  |     +-- 前序摘要
  |     +-- 对话记录 ([USER]: ... [ASSISTANT]: ...)
  |     +-- 可自定义：注册 PromptStrategy Bean 覆盖任意 prompt
  +-- 5. IntentSummarizer SPI 调用 LLM API
  +-- 6. 解析 JSON 响应为 IntentSummary
  +-- 7. 持久化到存储
  +-- 8. 自动提取记忆 --> 安全扫描 + 去重 + 置信度过滤 + 写入
```

### 上下文压缩流程

```
消息总量超过 context window 的 50%
  |
  +-- Phase 1: 工具输出裁剪（无 LLM 调用）
  |   +-- 保留最近 3 条工具消息原文，旧的替换为一行摘要
  |
  +-- Phase 2: 头部 + 尾部保护
  |   +-- 头部: 保护前 3 条消息
  |   +-- 尾部: 按Token预算保护最近的对话
  |
  +-- Phase 3: LLM 摘要中间部分
      +-- 发送给 LLM 生成紧凑摘要
```

## 构建

```bash
# 需要 Java 17+
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean compile

# 打包
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean package

# 安装到本地仓库
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean install
```

## 技术选型

| 项目 | 选择 | 说明 |
|------|------|------|
| Java | 17+ | 使用 java.net.http.HttpClient |
| JSON | 手动解析 | core 模块零外部依赖（Jackson optional） |
| 日志 | SLF4J | 仅门面，用户自选实现 |
| 测试 | JUnit 5 | -- |
| Spring Boot | 3.x | Starter 模块 optional |
| 构建 | Maven | -- |

## 项目结构

```
memory-agent/
├── pom.xml
├── README.md
├── MemoryAgent设计方案.md
├── memory-agent-core/
│   ├── pom.xml
│   └── src/main/java/com/ke/utopia/agent/memory/
│       ├── MemoryAgent.java                  # 主门面
│       ├── config/MemoryAgentConfig.java      # 配置（调优参数，无功能开关）
│       ├── model/                             # 数据模型
│       │   ├── MemoryEntry.java               # 记忆条目（含 tier, importance, accessCount）
│       │   ├── MemoryType.java                # MEMORY / USER_PROFILE
│       │   ├── MemoryTier.java                # CORE / ARCHIVED / RAW
│       │   ├── MemoryExtraction.java          # 自动提取结果
│       │   ├── ConflictResolution.java        # 冲突检测结果
│       │   ├── VectorSearchResult.java        # 语义搜索结果
│       │   ├── TurnContext.java               # 单轮上下文（processUserMessage 返回值）
│       │   ├── IncrementalIntent.java         # 增量意图
│       │   ├── MemorySnapshot.java            # 记忆快照
│       │   └── ... (Session, ConversationMessage, etc.)
│       ├── spi/                               # SPI 接口
│       │   ├── IntentSummarizer.java          # LLM 总结（含 extractMemories, detectConflict）
│       │   ├── MemoryStorage.java             # 存储（含 tier 相关方法）
│       │   ├── EmbeddingService.java          # 向量嵌入
│       │   ├── VectorStore.java               # 向量存储
│       │   ├── KeywordSearchService.java      # 关键词搜索
│       │   ├── PromptStrategy.java            # Prompt 构建策略（5 处 LLM prompt 可定制）
│       │   ├── PromptTemplate.java            # Prompt 模板值对象（system + user message）
│       │   ├── SummarizePromptContext.java     # 意图总结上下文
│       │   ├── CompressPromptContext.java      # 上下文压缩上下文
│       │   ├── MemoryExtractionPromptContext.java # 记忆提取上下文
│       │   ├── ConflictDetectionPromptContext.java # 冲突检测上下文
│       │   ├── IncrementalIntentPromptContext.java # 增量意图上下文
│       │   └── defaults/                      # 默认实现
│       │       ├── OpenAIIntentSummarizer.java # OpenAI 兼容 API（注入 PromptStrategy）
│       │       ├── DefaultPromptStrategy.java  # 默认 prompt 策略（中文）
│       │       ├── InMemoryMemoryStorage.java  # ConcurrentHashMap 存储
│       │       ├── SimpleEmbeddingService.java # TF-IDF 嵌入（零依赖）
│       │       ├── InMemoryVectorStore.java    # 内存向量存储
│       │       └── InMemoryKeywordSearchService.java # 内存关键词搜索
│       ├── memory/                            # 记忆管理
│       │   ├── CuratedMemoryManager.java       # 核心 CRUD（含冲突检测+向量化）
│       │   ├── MemoryTierManager.java          # 分层迁移
│       │   ├── MemoryDecayEngine.java          # 衰退引擎
│       │   ├── MemoryDecayStrategy.java        # 衰退策略接口
│       │   ├── TimeBasedDecayStrategy.java     # 时间衰减
│       │   ├── CompositeDecayStrategy.java     # 综合评分
│       │   ├── MemoryConflictDetector.java     # 冲突检测
│       │   ├── RelevanceTracker.java           # 重要性自学习
│       │   ├── MemoryMetricsCollector.java     # 指标收集
│       │   └── HybridSearchStrategy.java       # 混合检索 RRF
│       ├── intent/                            # 增量意图
│       │   ├── IncrementalIntentEngine.java    # 增量意图引擎
│       │   └── IntentContext.java              # 意图上下文
│       ├── summary/                           # 意图总结
│       │   ├── IntentSummarizationEngine.java  # 编排引擎（含自动提取）
│       │   ├── MemoryExtractor.java            # 记忆提取编排
│       │   ├── MemoryExtractionPrompt.java     # @Deprecated → DefaultPromptStrategy
│       │   ├── PromptConstructor.java          # @Deprecated → DefaultPromptStrategy
│       │   └── SummarizationConfig.java        # 总结配置
│       ├── conversation/                      # 会话管理
│       ├── compression/                       # 上下文压缩
│       ├── security/                          # 安全扫描
│       └── exception/                         # 自定义异常
├── memory-agent-spring-boot-starter/
│   ├── pom.xml
│   └── src/main/java/com/ke/utopia/agent/memory/spring/
│       ├── autoconfigure/                     # 自动装配（含 EmbeddingService/VectorStore 注入）
│       └── properties/                        # 配置属性（调优参数）
├── memory-agent-jdbc-storage/
│   ├── pom.xml
│   └── src/main/java/com/ke/utopia/agent/memory/jdbc/
│       ├── TkMapperMemoryStorage.java         # MySQL 持久化存储实现
│       ├── entity/                            # TkMapper Entity（@Table + getter/setter）
│       ├── mapper/                            # Mapper 接口（extends Mapper + 自定义 @Select）
│       ├── handler/                           # JSON TypeHandler
│       ├── converter/EntityConverter.java     # 领域模型 <-> Entity 转换
│       ├── autoconfigure/                     # Spring Boot 自动装配
│       └── spi/                               # SPI 注册
└── memory-agent-examples/
    ├── pom.xml
    └── src/main/java/com/ke/utopia/agent/memory/examples/
        ├── PureJavaExample.java               # 纯 Java 示例
        └── SpringBootExample.java             # Spring Boot 示例
```

## License

Private - All Rights Reserved
