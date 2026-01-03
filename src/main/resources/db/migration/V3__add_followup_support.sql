-- Phase 3: Async Follow-Up Handling
-- Add support for follow-up intent tracking

ALTER TABLE user_intent_inbox 
ADD COLUMN followup_parent_id BIGINT,
ADD CONSTRAINT fk_intent_followup_parent 
    FOREIGN KEY (followup_parent_id) 
    REFERENCES user_intent_inbox(id)
    ON DELETE CASCADE;

-- Index for finding follow-ups of a parent intent
CREATE INDEX idx_intent_followup_parent ON user_intent_inbox(followup_parent_id) WHERE followup_parent_id IS NOT NULL;

-- Index for finding pending follow-ups by user
CREATE INDEX idx_intent_needs_input_by_user ON user_intent_inbox(user_id, status) WHERE status = 'NEEDS_INPUT';
