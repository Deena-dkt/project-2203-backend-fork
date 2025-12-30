package com.apps.deen_sa.core.intent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for UserIntentInbox persistence operations.
 */
@Repository
public interface UserIntentInboxRepository extends JpaRepository<UserIntentInboxEntity, Long> {

    /**
     * Find by correlation ID for deduplication
     */
    Optional<UserIntentInboxEntity> findByCorrelationId(String correlationId);

    /**
     * Find all intents for a user with specific status
     */
    List<UserIntentInboxEntity> findByUserIdAndStatusOrderByReceivedAtDesc(String userId, IntentStatus status);

    /**
     * Find all pending intents ordered by received time (for processing queue)
     */
    List<UserIntentInboxEntity> findByStatusOrderByReceivedAt(IntentStatus status);

    /**
     * Check if correlation ID already exists
     */
    boolean existsByCorrelationId(String correlationId);

    /**
     * Phase 3: Find the most recent NEEDS_INPUT intent for a user
     * Used to correlate follow-up messages with pending intents
     */
    Optional<UserIntentInboxEntity> findFirstByUserIdAndStatusOrderByReceivedAtDesc(String userId, IntentStatus status);

    /**
     * Phase 3: Count NEEDS_INPUT intents for a user
     * Ensures only one unresolved follow-up at a time
     */
    long countByUserIdAndStatus(String userId, IntentStatus status);

    /**
     * Phase 3: Find all follow-ups for a parent intent
     */
    List<UserIntentInboxEntity> findByFollowupParentIdOrderByReceivedAt(Long parentId);
}
