-- Memory Agent Schema (MySQL 8.x)

CREATE TABLE IF NOT EXISTS ma_session (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    session_id        VARCHAR(36)   NOT NULL,
    user_id           VARCHAR(128)  NOT NULL,
    source            VARCHAR(64),
    model             VARCHAR(128),
    parent_session_id VARCHAR(36),
    started_at        DATETIME(6)   NOT NULL,
    ended_at          DATETIME(6),
    title             VARCHAR(512),
    message_count     INT           DEFAULT 0,
    status            VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_session_id (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ma_conversation_message (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    message_id   VARCHAR(36)  NOT NULL,
    session_id   VARCHAR(36)  NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    content      TEXT,
    tool_call_id VARCHAR(128),
    tool_name    VARCHAR(128),
    timestamp    DATETIME(6)  NOT NULL,
    token_count  INT          DEFAULT 0,
    metadata     JSON,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_message_id (message_id),
    INDEX idx_session_timestamp (session_id, timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ma_memory_entry (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    memory_id        VARCHAR(36)  NOT NULL,
    user_id          VARCHAR(128) NOT NULL,
    content          TEXT         NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    last_accessed_at DATETIME(6),
    access_count     INT          DEFAULT 0,
    importance_score DOUBLE       DEFAULT 0.5,
    tier             VARCHAR(16)  DEFAULT 'CORE',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_memory_id (memory_id),
    UNIQUE INDEX idx_dedup (user_id, content(255)),
    INDEX idx_user_type (user_id, type),
    INDEX idx_user_tier (user_id, tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ma_intent_summary (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    summary_id           VARCHAR(36)  NOT NULL,
    session_id           VARCHAR(36)  NOT NULL,
    user_id              VARCHAR(128) NOT NULL,
    core_intent          TEXT,
    key_topics           JSON,
    action_items         JSON,
    emotional_tone       VARCHAR(64),
    full_summary         TEXT,
    created_at           DATETIME(6)  NOT NULL,
    source_message_count INT          DEFAULT 0,
    total_tokens_used    BIGINT       DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_summary_id (summary_id),
    INDEX idx_session (session_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
