# Memory Agent — 项目开发指南

## 项目概述

Memory Agent 是一个多轮对话记忆中间件，为 AI Agent 提供会话管理、用户画像、意图总结、记忆存储、上下文压缩和安全扫描能力。

## 构建与运行

```bash
# Java 17（系统默认 1.8，必须设置 JAVA_HOME）
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean compile

# 打包
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean package -DskipTests

# 安装到本地仓库
JAVA_HOME=~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12/Contents/Home mvn clean install -DskipTests
```

## 项目结构

```
memory-agent/
├── memory-agent-core/                   51 个 Java 文件，4200 行
├── memory-agent-spring-boot-starter/    2 个 Java 文件
├── memory-agent-jdbc-storage/           MySQL + TkMapper 持久化存储（15 个 Java 文件）
└── memory-agent-examples/               2 个 Java 文件
```

- **GroupId:** `com.ke.utopia.agent`
- **Package:** `com.ke.utopia.agent.memory`
- **Java:** 17（GraalVM at `~/Library/Java/JavaVirtualMachines/graalvm-jdk-17.0.12`）

## 架构速览

```
MemoryAgent（门面）
├── ConversationManager      → 会话生命周期
├── CuratedMemoryManager     → 记忆 CRUD + 冲突检测 + 向量化
├── IntentSummarizationEngine → 意图总结 + 自动记忆提取
├── ContextCompressor        → 三阶段上下文压缩
├── SecurityScanner          → 三层安全扫描
├── MemoryTierManager        → 分层迁移 (CORE ↔ ARCHIVED)
├── MemoryDecayEngine        → 定时衰退评估
└── 全部依赖 → SPI 接口
     ├── MemoryStorage       → InMemoryMemoryStorage（默认）/ TkMapperMemoryStorage（MySQL）
     ├── IntentSummarizer    → OpenAIIntentSummarizer（默认）
     ├── EmbeddingService    → SimpleEmbeddingService（默认，零依赖）
     └── VectorStore         → InMemoryVectorStore（默认）
```

## 核心设计模式

- **SPI + ServiceLoader:** 存储、LLM、向量服务全部可插拔，META-INF/services 注册
- **不可变值对象:** MemoryEntry / ConversationMessage / Session 等均为 final + with* 方法
- **Builder 模式:** MemoryAgentConfig, Session, ConversationMessage, IntentSummary
- **冻结快照:** 会话启动时捕获，期间不变，保护 LLM Prefix Cache
- **配置开关:** 所有高级功能 boolean 开关，关闭 = 零开销 = v1.0 行为

## 关键包说明

| 包 | 职责 |
|---|------|
| `model/` | 14 个数据模型，全部不可变 |
| `spi/` | 5 个 SPI 接口 |
| `spi/defaults/` | 5 个默认实现 |
| `memory/` | 记忆管理（CRUD + 冲突检测 + 分层 + 衰退） |
| `summary/` | 意图总结 + 自动记忆提取 |
| `conversation/` | 会话管理 |
| `compression/` | 上下文压缩 |
| `security/` | 安全扫描 |
| `config/` | 配置对象 |
| `exception/` | 6 个自定义异常 |

## JDBC Storage 模块

`memory-agent-jdbc-storage` — MySQL + TkMapper 持久化存储后端

| 包 | 职责 |
|---|------|
| `jdbc/entity/` | 4 个 Entity（@Table + getter/setter），JSON 列存为 String |
| `jdbc/mapper/` | 4 个 Mapper（extends Mapper + 自定义 @Select） |
| `jdbc/handler/` | JSON TypeHandler（List\<String\> / Map\<String,String\>） |
| `jdbc/converter/` | EntityConverter（领域模型 ↔ Entity + JSON 序列化） |
| `jdbc/autoconfigure/` | Spring Boot 自动装配 + TkMapper MapperScan |
| `jdbc/spi/` | TkMapperStorageProvider（SPI 注册） |

### 技术栈

| 项目 | 选择 | 说明 |
|------|------|------|
| ORM | tk.mybatis 4.3.0 | 使用 javax.persistence 注解 |
| 数据库 | MySQL 8.x | 4 张表（schema.sql） |
| JSON | Jackson | EntityConverter 中序列化 JSON 列 |
| 注解 | javax.persistence | @Table, @Column, @Id（非 jakarta） |

### 数据库表

- `ma_session` — 会话信息
- `ma_conversation_message` — 对话消息（metadata 为 JSON 列）
- `ma_memory_entry` — 记忆条目（含去重索引 idx_dedup）
- `ma_intent_summary` — 意图总结（keyTopics/actionItems 为 JSON 列）

### 设计要点

- Entity 与领域模型分离：领域模型不可变（final + private constructor），Entity 有 getter/setter
- JSON 列在 Entity 中存为 String，由 EntityConverter 用 Jackson 序列化/反序列化
- TkMapperMemoryStorage 实现 MemoryStorage 21 个方法，行为与 InMemory 完全一致
- 自动装配：`TkMapperStorageAutoConfiguration` + `@ConditionalOnMissingBean(MemoryStorage.class)`
- core 模块唯一改动：`MemoryEntry.reconstruct()` 静态工厂方法

## SPI 扩展点

新增 SPI 方法均为 **default 方法**，已有实现无需修改：

- `IntentSummarizer.extractMemories()` — 记忆提取（默认返回空列表）
- `IntentSummarizer.detectConflict()` — 冲突检测（默认返回 KEEP_BOTH）
- `MemoryStorage.getMemoryEntriesByTier()` — 按层级查询
- `MemoryStorage.updateMemoryTier()` — 修改层级
- `MemoryStorage.getMemoryEntry()` — 按 ID 查单条
- `MemoryStorage.updateMemoryEntry()` — 替换整条

## v1.1 功能开关

| 配置项 | 默认 | 功能 |
|--------|------|------|
| `autoMemoryExtractionEnabled` | false | 总结时自动提取记忆 |
| `semanticSearchEnabled` | false | 写入自动向量化，支持语义搜索 |
| `decayEnabled` | false | 定时衰退评估，低分记忆归档 |
| `conflictDetectionEnabled` | false | 写入时 LLM 冲突检测 |
| 分层架构 | 默认启用 | MemoryTier: CORE/ARCHIVED/RAW |

## 编码规范

- **包路径:** `com.ke.utopia.agent.memory`
- **命名:** 类名大驼峰，方法名小驼峰，常量全大写下划线
- **日志:** SLF4J，`private static final Logger log = LoggerFactory.getLogger(Xxx.class)`
- **JSON:** core 模块不依赖 Jackson，手动解析（`extractJsonString`, `extractJsonArray`）
- **线程安全:** ConcurrentHashMap + compute() 原子操作，不可变值对象
- **异常体系:** MemoryAgentException 为基类，5 个子类各有明确语义

## 文档

- `README.md` — 用户使用文档（快速开始、API、配置、SPI 扩展）
- `docs/系统架构文档.md` — 架构设计文档（分层、数据模型、SPI 体系、线程模型）
- `MemoryAgent设计方案.md` — 原始设计方案
