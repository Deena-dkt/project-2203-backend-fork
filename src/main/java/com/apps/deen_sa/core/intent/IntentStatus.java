package com.apps.deen_sa.core.intent;

/**
 * Status of an intent in the inbox.
 * Represents the lifecycle of intent processing.
 * 
 * Phase 2 State Machine:
 * RECEIVED → PROCESSING → PROCESSED | NEEDS_INPUT | FAILED
 * 
 * Valid transitions:
 * - RECEIVED → PROCESSING (when starting processing)
 * - PROCESSING → PROCESSED (when successfully completed)
 * - PROCESSING → NEEDS_INPUT (when user input required)
 * - PROCESSING → FAILED (when processing fails)
 * - NEEDS_INPUT → PROCESSING (when user provides input and retry)
 * - FAILED → PROCESSING (on manual retry)
 */
public enum IntentStatus {
    RECEIVED,     // Intent received and persisted, awaiting processing (replaces PENDING)
    PROCESSING,   // Intent is currently being processed
    PROCESSED,    // Intent successfully processed (replaces COMPLETED)
    NEEDS_INPUT,  // Intent requires additional user input
    FAILED,       // Intent processing failed
    IGNORED       // Intent was intentionally not processed (e.g., invalid, spam)
}
