-- Phase 1: Intent Ingestion & Staging
-- Create user_intent_inbox table for intent staging before processing

CREATE TABLE IF NOT EXISTS user_intent_inbox (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(255) NOT NULL UNIQUE,
    raw_text TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL,
    detected_intent VARCHAR(100),
    intent_confidence NUMERIC(5,4),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status_reason TEXT,
    missing_fields JSONB,
    processing_attempts INTEGER NOT NULL DEFAULT 0,
    last_processed_at TIMESTAMP,
    
    CONSTRAINT chk_intent_confidence CHECK (intent_confidence IS NULL OR (intent_confidence >= 0 AND intent_confidence <= 1))
);

-- Index for efficient lookup by user and status
CREATE INDEX idx_user_intent_user_status ON user_intent_inbox(user_id, status);

-- Index for correlation_id lookups (for deduplication)
CREATE INDEX idx_user_intent_correlation ON user_intent_inbox(correlation_id);

-- Index for processing queue (PENDING intents ordered by received_at)
CREATE INDEX idx_user_intent_processing_queue ON user_intent_inbox(status, received_at) WHERE status = 'PENDING';
