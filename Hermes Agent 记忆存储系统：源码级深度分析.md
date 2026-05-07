

## 一、架构概览



Hermes Agent 的记忆系统采用了 **三层存储 + 双通道注入** 的设计，形成了一个完整的长期/短期记忆闭环：



```plain&#x20;text
┌─────────────────────────────────────────────────────────────────┐
│                        System Prompt                             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌───────────────┐  │
│  │  USER.md Snapshot │  │ MEMORY.md Snapshot│  │ Ext Provider  │  │
│  │  (用户画像-冻结)  │  │ (Agent笔记-冻结) │  │ (外部插件)    │  │
│  └──────────────────┘  └──────────────────┘  └───────────────┘  │
├─────────────────────────────────────────────────────────────────┤
│                     Conversation Context                         │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Context Compressor (上下文压缩器)                            ││
│  │  → 工具输出裁剪 → 头部保护 → 尾部Token预算 → LLM摘要      ││
│  └──────────────────────────────────────────────────────────────┘│
├─────────────────────────────────────────────────────────────────┤
│                     Tool Surface (工具层)                         │
│  ┌─────────┐  ┌───────────────┐  ┌────────────────────────────┐ │
│  │ memory  │  │ session_search│  │ External Provider Tools    │ │
│  │ (R/W)   │  │ (只读搜索)    │  │ (插件扩展)                │ │
│  └─────────┘  └───────────────┘  └────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
         │               │                      │
         ▼               ▼                      ▼
  ┌─────────────┐ ┌──────────────┐  ┌──────────────────────────┐
  │ MEMORY.md   │ │  state.db    │  │ Plugin Store (e.g.       │
  │ USER.md     │ │  (SQLite+FTS5)│  │   Honcho/Mnemosyne)     │
  │ (文件持久化) │ │  (会话历史)  │  │  (外部向量/图数据库)     │
  └─────────────┘ └──────────────┘  └──────────────────────────┘
```



**三种记忆层次：**



| 层次                    | 存储介质          | 持续时间  | 用途            | 文件位置                              |
| --------------------- | ------------- | ----- | ------------- | --------------------------------- |
| **Curated Memory**    | Markdown文件    | 跨会话永久 | 用户画像、Agent笔记  | `$HERMES_HOME/memories/MEMORY.md` |
| **Session History**   | SQLite + FTS5 | 跨会话永久 | 完整对话历史，支持全文搜索 | `$HERMES_HOME/state.db`           |
| **External Provider** | 插件定义          | 跨会话永久 | 语义记忆、向量检索等    | 由插件决定                             |



***



## 二、Curated Memory：核心记忆模块



### 2.1 存储格式



源码位置：`tools/memory_tool.py`



记忆以纯文本形式存储在两个 Markdown 文件中，使用 `§`（段落符号）作为条目分隔符：



```python
ENTRY_DELIMITER = "\n§\n"
```



**MEMORY.md 示例结构：**

```markdown
用户偏好使用 Python 3.12+ 的类型注解语法
项目使用 uv 作为包管理器，不用 pip
§
部署环境是 Ubuntu 22.04，已安装 Docker 24.x
§
用户的 coding style: 函数不超过 50 行，docstring 用 Google 风格
```



**USER.md 示例结构：**

```markdown
用户名: 张三，全栈工程师，时区 UTC+8
§
偏好中文交流，输出格式偏好飞书文档
§
对源码级深度分析感兴趣，喜欢从架构视角理解系统
```



### 2.2 MemoryStore 类设计



```python
class MemoryStore:
    def __init__(self, memory_char_limit: int = 2200, user_char_limit: int = 1375):
        self.memory_entries: List[str] = []      # 活跃状态（运行时）
        self.user_entries: List[str] = []         # 活跃状态（运行时）
        self.memory_char_limit = memory_char_limit  # MEMORY.md 字符上限 2200
        self.user_char_limit = user_char_limit      # USER.md 字符上限 1375
        # ⚠️ 关键：冻结快照，session 启动时捕获一次
        self._system_prompt_snapshot: Dict[str, str] = {"memory": "", "user": ""}
```



**设计要点：为什么选择字符数而非 Token 数？**



```python
# 源码注释：
# Character limits (not tokens) because char counts are model-independent.
```



这是一个深思熟虑的设计决策：不同模型的 tokenizer 差异很大（GPT-4 和 Claude 对中文的 token 计数完全不同），使用字符数作为上限确保了跨模型的一致性和可预测性。



### 2.3 冻结快照机制（Frozen Snapshot）



这是记忆系统最精巧的设计之一：



```python
def load_from_disk(self):
    """Load entries from MEMORY.md and USER.md, capture system prompt snapshot."""
    mem_dir = get_memory_dir()
    self.memory_entries = self._read_file(mem_dir / "MEMORY.md")
    self.user_entries = self._read_file(mem_dir / "USER.md")
    
    # 去重（保持顺序，保留首次出现）
    self.memory_entries = list(dict.fromkeys(self.memory_entries))
    self.user_entries = list(dict.fromkeys(self.user_entries))
    
    # 🔒 冻结快照：此后整个 session 期间不再改变
    self._system_prompt_snapshot = {
        "memory": self._render_block("memory", self.memory_entries),
        "user": self._render_block("user", self.user_entries),
    }
```



**为什么需要冻结？** 核心原因是 **Prefix Cache（前缀缓存）优化**：



```plain&#x20;text
System Prompt = [基础指令] + [MEMORY快照] + [USER快照] + [Skills列表] + ...
                    ↓
         这个前缀在整个 session 中保持不变
         → API 提供商可以缓存 prefix，降低延迟和成本
         → 如果每条 tool call 后 memory 变了，整个 prefix cache 就失效了
```



```python
def format_for_system_prompt(self, target: str) -> Optional[str]:
    """
    Return the frozen snapshot for system prompt injection.
    
    This returns the state captured at load_from_disk() time, NOT the live
    state. Mid-session writes do not affect this. This keeps the system
    prompt stable across all turns, preserving the prefix cache.
    """
    block = self._system_prompt_snapshot.get(target, "")
    return block if block else None
```



所以 Hermes 的记忆是 **"读时快照、写时持久、下个 session 生效"** 的模式：



```plain&#x20;text
Session A: memory writes → 立即写盘 → system prompt 不变（cache 安全）
Session B: 启动时加载 → 新快照 → system prompt 包含最新记忆
```



### 2.4 原子文件写入



记忆文件的写入采用了 **原子替换（atomic rename）** 模式，避免了并发写入的数据竞争：



```python
@staticmethod
def _write_file(path: Path, entries: List[str]):
    """Write entries to a memory file using atomic temp-file + rename.
    
    Previous implementation used open("w") + flock, but "w" truncates the
    file *before* the lock is acquired, creating a race window where
    concurrent readers see an empty file. Atomic rename avoids this:
    readers always see either the old complete file or the new one.
    """
    content = ENTRY_DELIMITER.join(entries) if entries else ""
    # 1. 写入临时文件（同目录，保证同一文件系统）
    fd, tmp_path = tempfile.mkstemp(
        dir=str(path.parent), suffix=".tmp", prefix=".mem_"
    )
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())          # fsync 确保数据落盘
        os.replace(tmp_path, str(path))    # 2. 原子替换
    except BaseException:
        try:
            os.unlink(tmp_path)            # 清理临时文件
        except OSError:
            pass
        raise
```



**为什么不用 `open("w") + flock`？** 源码注释解释得很清楚：`"w"` 模式在获取锁之前就已经截断了文件，这就创造了一个竞态窗口——并发读者可能看到一个空文件。`os.replace()` 在同一文件系统上是原子操作，读者要么看到旧文件，要么看到新文件，永远不会看到中间状态。



### 2.5 文件锁机制



虽然写入用了原子替换，但 read-modify-write 操作（add/replace/remove）仍然需要锁：



```python
@staticmethod
@contextmanager
def _file_lock(path: Path):
    """Acquire an exclusive file lock for read-modify-write safety.
    Uses a separate .lock file so the memory file itself can still be
    atomically replaced via os.replace().
    """
    lock_path = path.with_suffix(path.suffix + ".lock")
    
    if fcntl is None and msvcrt is None:
        yield        # 无锁可用（极少数情况）
        return
    
    fd = open(lock_path, "r+" if msvcrt else "a+")
    try:
        if fcntl:
            fcntl.flock(fd, fcntl.LOCK_EX)      # Unix: flock
        else:
            msvcrt.locking(fd.fileno(), msvcrt.LK_LOCK, 1)  # Windows
        yield
    finally:
        # 释放锁...
        fd.close()
```



关键点：锁文件和记忆文件是分开的（`.md.lock` vs `.md`），这样记忆文件的原子替换不会被锁文件影响。



### 2.6 安全防护：内容扫描



记忆内容会被注入到 system prompt 中，因此需要防止注入攻击和数据泄露：



```python
_MEMORY_THREAT_PATTERNS = [
    # 提示注入
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'you\s+are\s+now\s+', "role_hijack"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    (r'system\s+prompt\s+override', "sys_prompt_override"),
    
    # 数据泄露
    (r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET|PASSWORD)', "exfil_curl"),
    (r'cat\s+[^\n]*(\.env|credentials|\.netrc)', "read_secrets"),
    
    # 后门植入
    (r'authorized_keys', "ssh_backdoor"),
    (r'\$HOME/\.hermes/\.env', "hermes_env"),
]

# 不可见 Unicode 字符检测（防止隐写注入）
_INVISIBLE_CHARS = {
    '\u200b', '\u200c', '\u200d', '\u2060', '\ufeff',       # 零宽字符
    '\u202a', '\u202b', '\u202c', '\u202d', '\u202e',       # RTL/LTR 覆盖
}

def _scan_memory_content(content: str) -> Optional[str]:
    """扫描记忆内容中的注入/泄露模式。"""
    # 1. 检测不可见 Unicode
    for char in _INVISIBLE_CHARS:
        if char in content:
            return f"Blocked: content contains invisible unicode character U+{ord(char):04X}"
    
    # 2. 检测威胁模式
    for pattern, pid in _MEMORY_THREAT_PATTERNS:
        if re.search(pattern, content, re.IGNORECASE):
            return f"Blocked: content matches threat pattern '{pid}'"
    
    return None
```



### 2.7 CRUD 操作



所有修改操作都遵循 **Lock → Reload → Modify → Persist** 模式：



```python
def add(self, target: str, content: str) -> Dict[str, Any]:
    # 1. 安全扫描
    scan_error = _scan_memory_content(content)
    if scan_error:
        return {"success": False, "error": scan_error}
    
    with self._file_lock(self._path_for(target)):
        # 2. 锁内重读（获取其他 session 的最新写入）
        self._reload_target(target)
        
        # 3. 去重检查
        if content in entries:
            return self._success_response(target, "Entry already exists.")
        
        # 4. 容量检查
        new_total = len(ENTRY_DELIMITER.join(entries + [content]))
        if new_total > limit:
            return {"success": False, "error": "Memory limit exceeded."}
        
        # 5. 写入 + 持久化
        entries.append(content)
        self.save_to_disk(target)
    
    return self._success_response(target, "Entry added.")
```



`replace` 和 `remove` 使用子串模糊匹配定位目标条目（而非精确全文匹配或 ID），这是一个务实的工程选择——LLM 不太可能记住完整的记忆内容，但能回忆起关键片段。



***



## 三、Session History：SQLite + FTS5



### 3.1 数据库设计



源码位置：`hermes_state.py`



```sql
-- 会话表
CREATE TABLE sessions (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,           -- 'cli', 'telegram', 'discord', etc.
    user_id TEXT,
    model TEXT,
    system_prompt TEXT,
    parent_session_id TEXT,          -- 压缩/委托产生的子会话链
    started_at REAL NOT NULL,
    ended_at REAL,
    title TEXT,
    message_count INTEGER DEFAULT 0,
    tool_call_count INTEGER DEFAULT 0,
    input_tokens INTEGER DEFAULT 0,
    output_tokens INTEGER DEFAULT 0,
    estimated_cost_usd REAL,
    -- ... 更多计费和缓存字段
    FOREIGN KEY (parent_session_id) REFERENCES sessions(id)
);

-- 消息表
CREATE TABLE messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL REFERENCES sessions(id),
    role TEXT NOT NULL,              -- 'user', 'assistant', 'tool', 'system'
    content TEXT,
    tool_call_id TEXT,
    tool_calls TEXT,                 -- JSON 序列化
    tool_name TEXT,
    timestamp REAL NOT NULL,
    token_count INTEGER,
    reasoning TEXT,                  -- 推理链（v6 新增）
    reasoning_details TEXT,          -- 结构化推理（v6 新增）
    codex_reasoning_items TEXT       -- Codex 推理项（v6 新增）
);

-- FTS5 全文搜索虚拟表
CREATE VIRTUAL TABLE messages_fts USING fts5(
    content,
    content=messages,
    content_rowid=id
);

-- 自动同步触发器
CREATE TRIGGER messages_fts_insert AFTER INSERT ON messages BEGIN
    INSERT INTO messages_fts(rowid, content) VALUES (new.id, new.content);
END;
```



### 3.2 WAL 模式 + 写入重试



Hermes 通常有多个进程并发访问 state.db（gateway + CLI + worktree agents），因此写入竞争很常见：



```python
class SessionDB:
    _WRITE_MAX_RETRIES = 15
    _WRITE_RETRY_MIN_S = 0.020   # 20ms
    _WRITE_RETRY_MAX_S = 0.150   # 150ms
    _CHECKPOINT_EVERY_N_WRITES = 50
    
    def __init__(self, db_path=None):
        self._conn = sqlite3.connect(
            str(self.db_path),
            check_same_thread=False,
            timeout=1.0,              # SQLite 层面短超时
            isolation_level=None,     # 手动管理事务
        )
        self._conn.execute("PRAGMA journal_mode=WAL")  # Write-Ahead Logging
        self._conn.execute("PRAGMA foreign_keys=ON")
```



**为什么不用 SQLite 内置的 busy handler？** 源码注释揭示了一个精心设计的方案：



```python
def _execute_write(self, fn):
    """Execute a write transaction with BEGIN IMMEDIATE and jitter retry.
    
    SQLite's built-in busy handler uses a deterministic sleep schedule
    that causes convoy effects under high concurrency.
    
    Instead, we keep the SQLite timeout short (1s) and handle retries at
    the application level with random jitter, which naturally staggers
    competing writers and avoids the convoy.
    """
    for attempt in range(self._WRITE_MAX_RETRIES):
        try:
            with self._lock:
                self._conn.execute("BEGIN IMMEDIATE")  # 立即获取写锁
                result = fn(self._conn)
                self._conn.commit()
            
            # 成功后定期 WAL checkpoint
            self._write_count += 1
            if self._write_count % 50 == 0:
                self._try_wal_checkpoint()  # PASSIVE 模式，不阻塞
            return result
            
        except sqlite3.OperationalError as exc:
            if "locked" in err_msg or "busy" in err_msg:
                jitter = random.uniform(0.020, 0.150)  # 随机退避
                time.sleep(jitter)
                continue
```



这个设计的关键洞察是：SQLite 内置的 busy handler 使用确定性退避（每次等固定时间），在多进程并发时会产生 **convoy effect（护航效应）**——所有等待者几乎同时醒来竞争，然后又几乎同时失败。随机抖动打破了这种同步模式。



### 3.3 Session Search：FTS5 + LLM 摘要



源码位置：`tools/session_search_tool.py`



搜索流程分为三个阶段：



```plain&#x20;text
阶段 1: FTS5 全文搜索（毫秒级）
   query → FTS5 MATCH → 50 条匹配消息 → 按 session 分组 → 取 top N
   
阶段 2: 上下文截取（CPU 密集）
   每个匹配 session 加载完整对话 → 以匹配位置为中心截取窗口 → 最多 100K 字符
   
阶段 3: LLM 摘要（异步并行）
   每个截取后的对话 → Gemini Flash 摘要 → 返回结构化结果
```



**上下文截取策略**（`_truncate_around_matches`）是一个精巧的滑动窗口算法：



```python
def _truncate_around_matches(full_text, query, max_chars=100_000):
    # 优先级递减：
    # 1. 完整短语匹配（精确最高优先级）
    # 2. 所有关键词在 200 字符窗口内共现
    # 3. 任意关键词出现位置
    
    # 选择覆盖最多匹配位置的窗口
    for candidate in match_positions:
        ws = max(0, candidate - max_chars // 4)  # 25% 前置，75% 后置
        count = sum(1 for p in match_positions if ws <= p < ws + max_chars)
        if count > best_count:
            best_start = ws
```



**会话血缘解析**：压缩和委托任务会创建子 session（通过 `parent_session_id`），搜索时会向上追溯到根 session：



```python
def _resolve_to_parent(session_id):
    """Walk delegation chain to find the root parent session ID."""
    visited = set()
    sid = session_id
    while sid and sid not in visited:
        visited.add(sid)
        session = db.get_session(sid)
        parent = session.get("parent_session_id")
        if parent:
            sid = parent
        else:
            break
    return sid
```



***



## 四、Context Compressor：上下文压缩



源码位置：`agent/context_compressor.py`



当对话超出 context window 的 50%（默认阈值）时，压缩器自动触发：



```plain&#x20;text
完整对话: [System Prompt] [User1] [Asst1] [Tool1] ... [User20] [Asst20]
                │                                              │
                └── 头部保护（前3条） ──┐                    ┌── 尾部保护（~20K tokens）
                                         │                    │
                                    ┌─────┴────────────────────┴──────┐
                                    │     中间部分 → LLM 摘要         │
                                    └─────────────────────────────────┘
```



### 4.1 三阶段压缩管线



**阶段 1：工具输出裁剪（免费，无 LLM 调用）**



```python
def _prune_old_tool_results(self, messages, protect_tail_count, protect_tail_tokens):
    # Pass 1: 去重（MD5 哈希检测相同内容）
    # Pass 2: 旧结果替换为信息性摘要
    #   例如: "[terminal] ran `npm test` -> exit 0, 47 lines output"
    # Pass 3: 截断 assistant 消息中的大 tool_call 参数
```



每个工具都有定制的摘要函数（`_summarize_tool_result`），生成一行人类可读的描述：



```python
if tool_name == "terminal":
    return f"[terminal] ran `{cmd}` -> exit {exit_code}, {line_count} lines output"
if tool_name == "read_file":
    return f"[read_file] read {path} from line {offset} ({content_len:,} chars)"
```



**阶段 2：头部 + 尾部保护**



```python
# 头部：system prompt + 前几轮对话（不可压缩）
self.protect_first_n = 3

# 尾部：基于 token 预算而非固定消息数
self.tail_token_budget = target_tokens  # 动态计算
```



**阶段 3：LLM 摘要**



中间部分发给辅助模型（通常是便宜的 Gemini Flash）生成结构化摘要：



```python
SUMMARY_PREFIX = (
    "[CONTEXT COMPACTION — REFERENCE ONLY] Earlier turns were compacted "
    "into the summary below. This is a handoff from a previous context "
    "window — treat it as background reference, NOT as active instructions..."
)
```



### 4.2 反抖动保护



```python
def should_compress(self, prompt_tokens=None):
    # 如果最近两次压缩节省不到 10%，跳过压缩
    # 避免无限循环：每次只压缩掉 1-2 条消息
    if self._ineffective_compression_count >= 2:
        return False
```



***



## 五、Memory Provider Plugin System：可扩展记忆后端



### 5.1 插件发现机制



源码位置：`plugins/memory/__init__.py`



```plain&#x20;text
搜索路径:
  1. 内置插件: plugins/memory/<name>/  （随 Hermes 发布）
  2. 用户插件: $HERMES_HOME/plugins/<name>/ （用户安装）
  
  同名冲突时：内置优先
```



```python
def _iter_provider_dirs():
    seen = set()
    # 1. 扫描内置插件
    for child in sorted(_MEMORY_PLUGINS_DIR.iterdir()):
        if _is_valid_plugin_dir(child):
            seen.add(child.name)
            dirs.append((child.name, child))
    
    # 2. 扫描用户插件（跳过与内置同名的）
    for child in sorted(user_dir.iterdir()):
        if child.name not in seen and _is_memory_provider_dir(child):
            dirs.append((child.name, child))
```



### 5.2 插件加载



支持两种注册模式：



```python
def _load_provider_from_dir(provider_dir):
    # 模式 1: register(ctx) 函数（推荐方式）
    if hasattr(mod, "register"):
        collector = _ProviderCollector()
        mod.register(collector)          # 调用插件的注册函数
        return collector.provider
    
    # 模式 2: 自动发现 MemoryProvider 子类
    for attr_name in dir(mod):
        attr = getattr(mod, attr_name)
        if isinstance(attr, type) and issubclass(attr, MemoryProvider):
            return attr()
```



### 5.3 Memory Manager 桥接



外部记忆提供者通过 `MemoryManager` 与内置记忆系统桥接：



```python
# run_agent.py 中的初始化
self._memory_manager = MemoryManager()
self._memory_manager.add_provider(loaded_provider)

# 内置 memory tool 的写入会同步通知外部 provider
if self._memory_manager and function_args.get("action") in ("add", "replace"):
    self._memory_manager.on_memory_write(...)

# 压缩前通知外部 provider（让其保存即将丢失的上下文）
if self._memory_manager:
    self._memory_manager.on_pre_compress(messages)
```



***



## 六、数据流全景图



### 6.1 写入流程



```plain&#x20;text
用户说 "记住我喜欢用飞书文档格式"
        │
        ▼
Agent 调用 memory tool (action=add, target=user, content="偏好飞书文档格式")
        │
        ├── 1. _scan_memory_content() 安全扫描
        │       ├── 检测不可见 Unicode
        │       └── 检测注入/泄露模式
        │
        ├── 2. 获取 _file_lock()
        │
        ├── 3. _reload_target() 锁内重读磁盘最新数据
        │
        ├── 4. 去重 + 容量检查
        │
        ├── 5. 追加条目到 entries 列表
        │
        ├── 6. _write_file() 原子写入
        │       ├── tempfile.mkstemp() 创建临时文件
        │       ├── 写入 + fsync
        │       └── os.replace() 原子替换
        │
        ├── 7. 通知外部 MemoryProvider（如果有）
        │       └── self._memory_manager.on_memory_write(...)
        │
        └── 8. 返回成功响应（包含最新使用率）
                "10% — 220/2,200 chars"
```



### 6.2 读取流程



```plain&#x20;text
新 Session 启动
        │
        ├── 1. MemoryStore.load_from_disk()
        │       ├── 读取 MEMORY.md → memory_entries
        │       ├── 读取 USER.md → user_entries
        │       ├── 去重
        │       └── 🔒 捕获 _system_prompt_snapshot
        │
        ├── 2. 构造 System Prompt
        │       ├── format_for_system_prompt("user") → 冻结快照
        │       ├── format_for_system_prompt("memory") → 冻结快照
        │       ├── External provider.build_system_prompt()
        │       └── 拼接成完整 system prompt
        │
        └── 3. 整个 Session 期间 system prompt 中的记忆保持不变
                （写入只更新磁盘，不更新快照 → prefix cache 安全）
```



### 6.3 搜索流程



```plain&#x20;text
Agent 调用 session_search("docker 部署问题")
        │
        ├── 1. FTS5 全文搜索
        │       ├── _sanitize_fts5_query() 清理查询
        │       │   ├── 保留引号短语
        │       │   ├── 移除特殊字符
        │       │   └── 含连字符/点号的词加引号
        │       └── search_messages() → 50 条匹配
        │
        ├── 2. 按 session 分组 + 排除当前 session
        │       ├── _resolve_to_parent() 追溯根 session
        │       └── 取 top N 个唯一 session
        │
        ├── 3. 加载 + 截取对话
        │       ├── get_messages_as_conversation() 完整对话
        │       └── _truncate_around_matches() 以匹配为中心的窗口
        │
        └── 4. 异步并行摘要
                ├── _summarize_all() → asyncio.gather()
                └── 返回结构化摘要结果
```



***



## 七、设计亮点与权衡



### 亮点



1. **冻结快照 + Prefix Cache**：记忆注入 system prompt 后冻结，让 API 提供商的前缀缓存生效，每次 API 调用可节省大量 token



2. **原子文件写入**：使用 `tempfile + os.replace()` 替代 `open("w") + flock`，彻底消除了截断竞态窗口



3) **三层记忆分离**：Curated Memory（精确/小容量）、Session History（完整/可搜索）、External Provider（可扩展），各司其职



4) **安全纵深防御**：不可见字符检测 + 正则注入模式扫描 + 数据泄露模式检测，三层保护记忆注入面



5. **随机抖动重试**：用应用层随机退避替代 SQLite 确定性退避，有效打破多进程写入的护航效应



6. **FTS5 + LLM 摘要的两阶段搜索**：先用数据库快速过滤，再用 LLM 精炼结果，兼顾速度和质量



### 权衡



1. **记忆容量有限**（2200 + 1375 字符）：这是刻意的约束——迫使 Agent 只存最关键的信息，避免 context window 被无用的历史数据占满



2. **记忆更新延迟**：写入的记忆要到下一个 session 才会出现在 system prompt 中（当前 session 只影响工具返回的实时状态）



3) **字符串匹配而非语义匹配**：replace/remove 操作用子串匹配，不是向量相似度——简单可靠但不理解语义



4) **单 Provider 限制**：同一时间只能激活一个外部记忆提供者，设计上选择了简单性而非灵活性



***



## 八、Schema 版本迁移



state.db 经历了 6 个版本的演进，展示了系统的迭代轨迹：



| 版本 | 变更                             |
| -- | ------------------------------ |
| v1 | 初始 schema（sessions + messages） |
| v2 | messages 表新增 `finish_reason`   |
| v3 | sessions 表新增 `title`           |
| v4 | title 唯一索引（部分索引，NULL 允许）       |
| v5 | 新增 token 计数、计费、缓存相关字段（10 列）    |
| v6 | 新增 reasoning 相关字段（推理链持久化）      |



迁移采用 `ALTER TABLE ADD COLUMN` + 版本号追踪，每次只执行增量迁移。



***



## 九、System Prompt 组装：记忆注入的全景视图



### 9.1 Prompt Builder 的角色



源码位置：`agent/prompt_builder.py` + `run_agent.py` `_build_system_prompt()`



`prompt_builder.py` 是 Hermes 的 **System Prompt 装配车间**——它负责将所有静态和动态的上下文信息按严格顺序组装成最终的 system prompt。记忆注入是这个装配流水线中最关键的环节之一。



### 9.2 System Prompt 的 10 层组装顺序



```python
# run_agent.py: _build_system_prompt() 的完整流程
def _build_system_prompt(self, system_message=None):
    prompt_parts = []
    
    # Layer 1: Agent 身份 — SOUL.md（自定义人格）或 DEFAULT_AGENT_IDENTITY
    if soul_content:
        prompt_parts.append(soul_content)      # 个性化人格
    else:
        prompt_parts.append(DEFAULT_AGENT_IDENTITY)  # 默认身份
    
    # Layer 2: 工具感知行为引导（按工具存在与否条件注入）
    if "memory" in tools:
        prompt_parts.append(MEMORY_GUIDANCE)           # 记忆使用指南
    if "session_search" in tools:
        prompt_parts.append(SESSION_SEARCH_GUIDANCE)    # 跨会话搜索指南
    if "skill_manage" in tools:
        prompt_parts.append(SKILLS_GUIDANCE)            # 技能创建指南
    
    # Layer 3: Nous 订阅能力说明
    prompt_parts.append(nous_subscription_prompt)
    
    # Layer 4: 工具使用执行纪律（模型特定）
    if model in TOOL_USE_ENFORCEMENT_MODELS:
        prompt_parts.append(TOOL_USE_ENFORCEMENT_GUIDANCE)
        if "gemini" in model:
            prompt_parts.append(GOOGLE_MODEL_OPERATIONAL_GUIDANCE)
        if "gpt" in model:
            prompt_parts.append(OPENAI_MODEL_EXECUTION_GUIDANCE)
    
    # Layer 5: 用户提供的自定义 system prompt
    if system_message:
        prompt_parts.append(system_message)
    
    # Layer 6: 🔑 持久记忆注入（冻结快照）
    if self._memory_store:
        if self._memory_enabled:
            mem_block = self._memory_store.format_for_system_prompt("memory")
            prompt_parts.append(mem_block)        # MEMORY.md 快照
        if self._user_profile_enabled:
            user_block = self._memory_store.format_for_system_prompt("user")
            prompt_parts.append(user_block)       # USER.md 快照
    
    # Layer 7: 外部记忆提供者（Honcho/Mem0 等）
    if self._memory_manager:
        ext_block = self._memory_manager.build_system_prompt()
        prompt_parts.append(ext_block)
    
    # Layer 8: 技能索引（两层缓存：LRU + 磁盘快照）
    prompt_parts.append(skills_prompt)
    
    # Layer 9: 上下文文件（AGENTS.md / .cursorrules / .hermes.md）
    prompt_parts.append(context_files_prompt)
    
    # Layer 10: 时间戳 + 模型信息 + 平台提示
    prompt_parts.append(timestamp_line)
    prompt_parts.append(environment_hints)
    prompt_parts.append(platform_hints)
    
    return "\n\n".join(prompt_parts)
```



### 9.3 记忆注入的关键设计决策



#### 决策 1：记忆位置在 System Prompt 中段偏后



```plain&#x20;text
[身份] → [行为引导] → [自定义prompt] → [🧠 记忆快照] → [技能] → [上下文] → [时间戳]
```



为什么不在最前面？

* System Prompt 的开头（身份定义）和结尾（平台提示）被 API 提供商的 Prefix Cache 最优先缓存

* 记忆快照位于中间层，既不被截断（有头部保护），也不会影响核心身份的前缀缓存



#### 决策 2：工具感知的条件注入



```python
# 只有当 memory tool 被启用时才注入记忆使用指南
if "memory" in self.valid_tool_names:
    tool_guidance.append(MEMORY_GUIDANCE)
```



这避免了"告诉 Agent 使用一个它根本没有的工具"——在 cron 模式或受限模式下，memory tool 可能被禁用。



#### 决策 3：技能索引的两层缓存



```python
# Layer 1: 进程内 LRU 缓存（8 条上限）
_SKILLS_PROMPT_CACHE: OrderedDict[tuple, str] = OrderedDict()

# Layer 2: 磁盘快照（mtime/size 校验）
# .skills_prompt_snapshot.json — 冷启动时直接读取，无需扫描文件系统
```



技能索引可能包含 100+ 个技能的描述，每次从文件系统扫描耗时 50-200ms。磁盘快照通过 mtime+size 双重校验确保数据新鲜度，命中时直接反序列化，冷启动从 \~200ms 降到 <5ms。



### 9.4 上下文文件的优先级发现



`prompt_builder.py` 实现了一个优雅的上下文文件发现链：



```plain&#x20;text
优先级（先到先得，只加载一个项目上下文）:
  .hermes.md / HERMES.md  → 向上遍历到 git root
  AGENTS.md / agents.md   → 仅当前目录
  CLAUDE.md / claude.md   → 仅当前目录
  .cursorrules             → 仅当前目录
```



**设计洞察**：`.hermes.md` 支持 **向上遍历到 git root**，这意味着你可以在项目根目录放一个 `.hermes.md`，无论在哪个子目录启动 Hermes 都能发现它。其他文件（AGENTS.md、CLAUDE.md）只在当前目录查找，避免父目录的配置污染。



### 9.5 安全扫描：上下文文件的双重防护



与记忆系统的安全扫描（`memory_tool.py`）类似，`prompt_builder.py` 也有自己的注入检测：



```python
# prompt_builder.py 的威胁模式（比 memory_tool.py 更全面）
_CONTEXT_THREAT_PATTERNS = [
    # 提示注入
    (r'ignore\s+(previous|all|above|prior)\s+instructions', "prompt_injection"),
    (r'do\s+not\s+tell\s+the\s+user', "deception_hide"),
    (r'system\s+prompt\s+override', "sys_prompt_override"),
    
    # 新增：更多注入模式
    (r'disregard\s+(your|all|any)\s+(instructions|rules)', "disregard_rules"),
    (r'act\s+as\s+(if|though)\s+you\s+(have\s+no)', "bypass_restrictions"),
    (r'<!--[^>]*(?:ignore|override|system|secret)', "html_comment_injection"),
    (r'<\s*div\s+style.*display\s*:\s*none', "hidden_div"),
    (r'translate\s+.*\s+into\s+.*\s+and\s+(execute|run)', "translate_execute"),
    
    # 数据泄露
    (r'curl\s+[^\n]*\$\{?\w*(KEY|TOKEN|SECRET)', "exfil_curl"),
    (r'cat\s+[^\n]*(\.env|credentials|\.netrc)', "read_secrets"),
]
```



**与 memory\_tool.py 的安全扫描对比**：



| 维度            | memory\_tool.py  | prompt\_builder.py             |
| ------------- | ---------------- | ------------------------------ |
| **扫描对象**      | 用户/Agent 写入的记忆内容 | 上下文文件（AGENTS.md 等）             |
| **威胁模式数**     | 8 个              | 10 个（更多）                       |
| **HTML 注入检测** | ❌ 无              | ✅ 检测隐藏 div 和 HTML 注释           |
| **翻译执行攻击**    | ❌ 无              | ✅ 检测 "translate...and execute" |
| **失败处理**      | 返回错误，拒绝写入        | 替换为 `[BLOCKED: ...]` 占位符       |



### 9.6 上下文文件截断策略



每个上下文文件有 20,000 字符上限，超出时采用 **70/10/20** 分配策略：



```python
CONTEXT_FILE_MAX_CHARS = 20_000
CONTEXT_TRUNCATE_HEAD_RATIO = 0.7   # 保留头部 14,000 字符
CONTEXT_TRUNCATE_TAIL_RATIO = 0.2   # 保留尾部 4,000 字符
# 中间 10% = 插入截断标记
```



```plain&#x20;text
原始文件: [================ 20K+ chars ================]
截断后:   [==== 14K head ====][...truncated...][=== 4K tail ===]
```



为什么保留尾部？因为 AGENTS.md 等文件经常在末尾放重要的构建命令和安全规则。



### 9.7 完整 System Prompt 结构图



```plain&#x20;text
┌──────────────────────────────────────────────────────────────────┐
│                    System Prompt 总览                             │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ 1. SOUL.md / DEFAULT_IDENTITY          [身份层 - 可缓存]    │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 2. MEMORY_GUIDANCE                      [行为引导层]       │ │
│  │ 3. SESSION_SEARCH_GUIDANCE                                 │ │
│  │ 4. SKILLS_GUIDANCE                                         │ │
│  │ 5. TOOL_USE_ENFORCEMENT (模型特定)                          │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 6. User system_message                  [自定义层]         │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 7. 🧠 MEMORY.md snapshot (冻结)        [记忆层 - 可缓存]   │ │
│  │ 8. 👤 USER.md snapshot (冻结)                              │ │
│  │ 9. 🔌 External provider block                              │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 10. Skills index (LRU+磁盘缓存)        [技能层]            │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 11. Context files (AGENTS.md等)         [上下文层]         │ │
│  ├────────────────────────────────────────────────────────────┤ │
│  │ 12. Timestamp + Model + Platform        [环境层 - 可缓存]   │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  总大小：通常 8K-30K tokens（取决于技能数和上下文文件大小）        │
│  缓存策略：整个 prompt 一次构建后冻结，session 内不变              │
└──────────────────────────────────────────────────────────────────┘
```



### 9.8 缓存优化总结



| 层级                  | 缓存机制                | 命中率   | 节省                 |
| ------------------- | ------------------- | ----- | ------------------ |
| **System Prompt**   | Prefix Cache (冻结快照) | \~95% | 每次调用节省数K tokens    |
| **Skills Index**    | LRU (8条) + 磁盘快照     | \~80% | 冷启动从 200ms → 5ms   |
| **Memory Snapshot** | Session 内冻结         | 100%  | 避免 prefix cache 失效 |
| **Context Files**   | 无缓存（每次构建时读取）        | N/A   | 受 20K 字符上限约束       |



***



*本文基于 Hermes Agent 源码分析，涉及的核心文件：*

* `tools/memory_tool.py` — 记忆工具（MemoryStore + 安全扫描）

* `hermes_state.py` — SQLite 会话存储（SessionDB + FTS5）

* `tools/session_search_tool.py` — 会话搜索工具

* `agent/context_compressor.py` — 上下文压缩器

* `agent/memory_provider.py` — 记忆提供者 ABC

* `agent/memory_manager.py` — 记忆管理器（桥接层）

* `agent/prompt_builder.py` — System Prompt 组装（新增）

* `plugins/memory/__init__.py` — 插件发现与加载

* `run_agent.py` — 主运行时（记忆初始化与注入）
