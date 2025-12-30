package com.apps.deen_sa.core.intent;

/**
 * Status of an intent in the inbox.
 * Represents the lifecycle of intent processing.
 */
public enum IntentStatus {
    PENDING,      // Intent received and persisted, awaiting processing
    PROCESSING,   // Intent is currently being processed
    COMPLETED,    // Intent successfully processed
    FAILED,       // Intent processing failed
    IGNORED       // Intent was intentionally not processed (e.g., invalid, spam)
}
