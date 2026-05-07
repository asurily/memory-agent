-- Memory Agent Schema (MySQL 8.x)

-- =====================================================
-- 会话表：存储用户会话信息
-- =====================================================
CREATE TABLE IF NOT EXISTS ma_session (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
    session_id        VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '会话唯一标识UUID(业务ID)',
    user_id           VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '用户ID',
    source            VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '会话来源(cli/api/web)',
    model             VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '使用的LLM模型名称',
    parent_session_id VARCHAR(36)   NOT NULL DEFAULT '' COMMENT '父会话ID，用于会话继承',
    started_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '会话开始时间',
    ended_at          DATETIME(6)   NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '会话结束时间',
    title             VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '会话标题',
    message_count     INT           NOT NULL DEFAULT 0 COMMENT '消息数量',
    status            VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE' COMMENT '会话状态(ACTIVE/CLOSED)',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_session_id (session_id),
    INDEX idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话信息表';

-- =====================================================
-- 对话消息表：存储会话中的对话消息
-- =====================================================
CREATE TABLE IF NOT EXISTS ma_conversation_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
    message_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '消息唯一标识UUID(业务ID)',
    session_id   VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '所属会话ID',
    role         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '消息角色(USER/ASSISTANT/SYSTEM/TOOL)',
    content      TEXT         COMMENT '消息内容',
    tool_call_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '工具调用ID',
    tool_name    VARCHAR(128) NOT NULL DEFAULT '' COMMENT '工具名称',
    timestamp    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '消息时间戳',
    token_count  INT          NOT NULL DEFAULT 0 COMMENT 'Token数量',
    metadata     JSON         COMMENT '扩展元数据(JSON格式)',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_message_id (message_id),
    INDEX idx_session_timestamp (session_id, timestamp)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

-- =====================================================
-- 记忆条目表：存储用户画像和记忆
-- =====================================================
CREATE TABLE IF NOT EXISTS ma_memory_entry (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
    memory_id        VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '记忆唯一标识UUID(业务ID)',
    user_id          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户ID',
    content          TEXT         COMMENT '记忆内容',
    type             VARCHAR(32)  NOT NULL DEFAULT 'MEMORY' COMMENT '记忆类型(USER_PROFILE/MEMORY)',
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    last_accessed_at DATETIME(6)  NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '最后访问时间',
    access_count     INT          NOT NULL DEFAULT 0 COMMENT '访问次数',
    importance_score DOUBLE       NOT NULL DEFAULT 0.5 COMMENT '重要性评分(0.0-1.0)',
    tier             VARCHAR(16)  NOT NULL DEFAULT 'CORE' COMMENT '记忆层级(CORE/ARCHIVED/RAW)',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_memory_id (memory_id),
    UNIQUE INDEX idx_dedup (user_id, content(255)),
    INDEX idx_user_type (user_id, type),
    INDEX idx_user_tier (user_id, tier)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记忆条目表';

-- =====================================================
-- 意图总结表：存储会话意图总结
-- =====================================================
CREATE TABLE IF NOT EXISTS ma_intent_summary (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '数据库自增主键',
    summary_id           VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '总结唯一标识UUID(业务ID)',
    session_id           VARCHAR(36)  NOT NULL DEFAULT '' COMMENT '所属会话ID',
    user_id              VARCHAR(128) NOT NULL DEFAULT '' COMMENT '用户ID',
    core_intent          TEXT         COMMENT '核心意图',
    key_topics           JSON         COMMENT '关键主题(JSON数组)',
    action_items         JSON         COMMENT '待办事项(JSON数组)',
    emotional_tone       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '情感基调',
    full_summary         TEXT         COMMENT '完整总结',
    created_at           DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    source_message_count INT          NOT NULL DEFAULT 0 COMMENT '源消息数量',
    total_tokens_used    BIGINT       NOT NULL DEFAULT 0 COMMENT '总Token消耗',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_summary_id (summary_id),
    INDEX idx_session (session_id),
    INDEX idx_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='意图总结表';
