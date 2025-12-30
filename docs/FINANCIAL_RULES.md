# Financial Rules & Invariants

**This document is the authoritative source of financial truth for this application.**

Code and tests must comply with this document.

---

## Purpose and Rule Hierarchy

**Financial rules are defined in natural language.**

Integration tests enforce these rules. Production code must pass tests that enforce these rules.

### Rule Hierarchy

```
Documents > Tests > Code
```

1. **Documents** (this file): Define financial correctness rules in natural language
2. **Tests** (`src/test/java/.../simulation/`): Enforce rules via integration tests
3. **Code** (`src/main/java/.../finance/`): Implements rules, verified by tests

**Documents outrank code.**

If code contradicts a documented rule, the code is wrong.

**Never implement financial logic without a corresponding rule and test.**

---

## 1. Core Invariants

These rules must hold true for all financial flows.
Violating any rule is a system defect.

### Invariant 1: No Duplicate Financial Application

A transaction must apply financial impact at most once.

- A transaction marked `financiallyApplied = true`
  must never produce additional value adjustments.
- Reprocessing the same transaction must be idempotent.

Example:
- Given a credit card expense of ₹1,000
- When the system retries processing
- Then the credit outstanding increases only once

---

## 2. Value Container Behavior Rules

### Cash & Bank Containers (Asset Containers)
- Debit reduces currentValue
- Credit increases currentValue
- currentValue must never be negative
- Both Cash and Bank containers follow identical asset container rules

### Credit Card Container
- Debit (expense) increases outstanding balance
- Payment reduces outstanding balance
- Outstanding must not exceed credit limit unless explicitly allowed
- Over-limit flag must reflect actual state

---

## 3. Canonical Transaction Scenarios

### Scenario: Typical Credit Card Month

- Day 1: Grocery expense ₹1,200 (credit card)
- Day 5: Fuel expense ₹2,000 (credit card)
- Day 15: Credit card payment ₹3,000

Expected outcomes:
- Credit outstanding = ₹200
- No cash balance change
- Exactly 3 transactions
- Exactly 3 value adjustments

---

## 4. Edge Cases to Always Support

### Duplicate Events
- Same transaction processed twice
- Same payment received twice

### Ordering Issues
- Payment before expense
- Expense after statement close

### Partial Failures
- Adjustment succeeds but transaction save fails
- Retry after crash

---

## 5. System Assumptions

- All financial calculations are deterministic
- LLMs never perform calculations
- Database is the source of truth
- Event retries are expected and normal

---

## Using These Financial Rules

### For Integration Test Development

When writing integration tests:

1. Read the relevant rule section in this document
2. Map rule to test scenario
3. Write test that fails if rule violated
4. Reference this document in test documentation

Example:
```java
/**
 * Tests enforcement of FINANCIAL_RULES.md - Section 1, Invariant 1
 * "A transaction marked financiallyApplied = true must never 
 *  produce additional value adjustments"
 */
@Test
void testIdempotency() {
    // test implementation
}
```

### For Production Code Development

When implementing financial features:

1. Check if relevant rule exists in this document
2. If not, write rule first (add to this document)
3. Write integration test enforcing rule
4. Implement code that passes test

---

## Adding New Rules

### When to Add a Rule

Add a rule when:

- Introducing new financial behavior
- Fixing a production financial bug
- Adding new transaction type
- Adding new container type

### How to Add a Rule

1. **Choose the right section**:
   - Core financial law → Section 1 (Core Invariants)
   - Account behavior → Section 2 (Container Behavior)
   - Standard workflow → Section 3 (Canonical Scenarios)
   - Edge case → Section 4 (Edge Cases)
   - System assumption → Section 5 (Assumptions)

2. **Write the rule clearly**:
   - Use simple language
   - Be explicit
   - Provide examples
   - Avoid ambiguity

3. **Bad rule** (ambiguous):
   > "Transactions should be processed correctly"

4. **Good rule** (explicit):
   > "A transaction marked `financiallyApplied = true` must never produce additional value adjustments. Reprocessing the same transaction must be idempotent."

5. **Add integration test**:
   - Test must enforce new rule
   - Reference this document in test documentation
   - Verify test fails when rule violated

6. **Update production code**:
   - Code passes test enforcing rule

---

## Production Bugs Must Update Rules First

### Bug Fix Process

When a production financial bug is discovered:

1. **Reproduce the bug** in integration test (should fail)
2. **Add or clarify rule** in appropriate section of this document
3. **Update integration test** to enforce clarified rule
4. **Fix production code** to pass test
5. **Verify** all financial invariants still hold

**Do NOT fix code first.** Document expected behavior, then fix code.

### Example Bug Fix Flow

**Bug**: Payment processed twice reduces credit card balance twice

**Process**:
1. Add failing test: `testPaymentIdempotency()`
2. Add rule to this document (Section 1: Core Invariants):
   ```markdown
   ## Invariant: Payment Idempotency
   
   A payment marked `financiallyApplied = true` must never 
   reduce outstanding balance again, even if reprocessed.
   ```
3. Fix code: Add `if (tx.isFinanciallyApplied()) return;`
4. Verify test passes
5. Verify all 8 financial invariants pass

---

## Rule Interpretation

### Rules Are Non-Negotiable Contracts

Financial rules are:
- ✅ Explicit requirements
- ✅ Testable specifications
- ✅ Production guarantees

Financial rules are NOT:
- ❌ Implementation suggestions
- ❌ Performance guidelines
- ❌ Negotiable trade-offs

### Ambiguous Rules Must Be Clarified

If a rule is unclear:

1. **Flag it** in code review or team discussion
2. **Clarify the rule** with examples
3. **Update this document**
4. **Update tests** to match clarification

**Do NOT implement ambiguous rules.** Clarify first.

---

## Test Coverage Verification

### Coverage Audit

Integration tests must enforce all documented rules.

To verify coverage:

```bash
# Run all integration tests
mvn clean verify -Pintegration

# Check coverage analysis
cat docs/FINANCIAL_RULES_TEST_COVERAGE.md
```

See `docs/FINANCIAL_RULES_TEST_COVERAGE.md` for rule-by-rule mapping to test coverage.

### Required Invariants

All integration tests must verify these 8 invariants after each simulation:

1. No orphan adjustments
2. Adjustment-transaction consistency
3. Balance integrity
4. Money conservation
5. No negative balances (assets)
6. Capacity limits respected (liabilities)
7. Transaction validity
8. Idempotency

Enforced by: `FinancialAssertions.assert*()` methods

---

## Document Maintenance

### Keep Rules Up-to-Date

Rules must be updated when:

- Business requirements change
- New financial products added
- Regulatory requirements change
- Production bugs discovered

### Don't Duplicate Rules

**DO NOT** duplicate rules across multiple files or sections.

**Wrong**:
```markdown
# In Section 1: Core Invariants
Payment must be idempotent

# In Section 4: Edge Cases
Payment processed twice should not double-deduct
```

**Right**:
```markdown
# In Section 1: Core Invariants
Payment must be idempotent

# In Section 4: Edge Cases
See Section 1 for payment idempotency rule
```

### Keep Rules Concise

Each rule should:
- State one specific requirement
- Include concrete example
- Be testable

**Too vague**: "Handle errors gracefully"  
**Specific**: "If payment fails, rollback all adjustments and mark transaction as ERROR"

---

## Financial Correctness Ownership

### Who Owns These Rules?

**Backend Engineering Team**

Responsibilities:
- Keep rules current
- Ensure test coverage
- Review code against rules
- Update rules when requirements change

### Code Review Checklist

When reviewing financial code:

- [ ] Is there a documented rule for this behavior?
- [ ] Is there an integration test enforcing the rule?
- [ ] Does the code pass the test?
- [ ] Are all 8 financial invariants verified?

**If any answer is "no", request changes.**

---

## CI/CD Integration

### Pull Request Validation

GitHub Actions runs:
```bash
mvn clean verify -Pintegration -Dfuzz.iterations=50
```

**PR merge blocked if**:
- Any integration test fails
- Financial invariants violated
- Code doesn't match documented rules

### Nightly Testing

Comprehensive fuzz testing runs nightly:
```bash
mvn verify -Pintegration -Dfuzz.iterations=100
```

Failures create GitHub issues automatically.

---

## Examples

### Good Rule Definition

From Section 1 (Core Invariants):

```markdown
## Invariant 1: No Duplicate Financial Application

A transaction must apply financial impact at most once.

- A transaction marked `financiallyApplied = true` 
  must never produce additional value adjustments.
- Reprocessing the same transaction must be idempotent.

Example:
- Given a credit card expense of ₹1,000
- When the system retries processing
- Then the credit outstanding increases only once
```

**Why it's good**:
- Clear requirement
- Explicit flag mentioned (`financiallyApplied`)
- Concrete example with amounts
- Testable behavior

### Integration Test Enforcing Rule

From `MonthlySimulationIT.java`:

```java
/**
 * Enforces: FINANCIAL_RULES.md - Section 1, Invariant 1
 * "No Duplicate Financial Application - Rerunning simulation should not change balances"
 */
@Test
void rerunSimulationIsIdempotent() {
    // Run simulation once
    FinancialSimulationRunner.simulate(ctx)
        .day(1).setupContainer("BANK_ACCOUNT", "My Bank", 100000)
        .day(3).expense("Groceries", 1200, "CREDIT_CARD")
        .run();
    
    // Capture balances
    Map<Long, BigDecimal> before = captureBalances();
    
    // Rerun same simulation
    FinancialSimulationRunner.simulate(ctx)
        .day(1).setupContainer("BANK_ACCOUNT", "My Bank", 100000)
        .day(3).expense("Groceries", 1200, "CREDIT_CARD")
        .run();
    
    // Assert unchanged
    Map<Long, BigDecimal> after = captureBalances();
    assertEquals(before, after, "Balances changed on rerun - violates idempotency");
}
```

---

---

## 6. Phase 1: Intent Ingestion & Staging Contract

**This section defines the contract for Phase 1 intent ingestion.**

### Intent Inbox Purpose

The `user_intent_inbox` table is a **staging area** for all incoming user intents.

- Acts as a write-ahead log
- Ensures durability before processing
- Enables immediate acknowledgment
- Prevents data loss on processing failures

### Intent Inbox Schema

Table: `user_intent_inbox`

Required columns (exact schema):
- `id` - Primary key
- `user_id` - User identifier (VARCHAR)
- `channel` - Origin channel (e.g., WHATSAPP, WEB)
- `correlation_id` - Unique deduplication key
- `raw_text` - Original user input
- `received_at` - Timestamp of receipt
- `detected_intent` - Intent type (nullable, set after classification)
- `intent_confidence` - Classification confidence (nullable)
- `status` - Processing status (PENDING, PROCESSING, COMPLETED, FAILED, IGNORED)
- `status_reason` - Explanation for status
- `missing_fields` - JSON of missing required fields
- `processing_attempts` - Retry counter
- `last_processed_at` - Last processing timestamp

### Intent Inbox Invariants

These rules must hold for all intent ingestion:

#### Invariant 1: Immediate Persistence

User intents must be persisted to `user_intent_inbox` **immediately** upon receipt.

- Persistence happens **before** any processing
- Persistence happens **before** LLM calls
- Persistence enables immediate acknowledgment (< 500ms)

Example:
- Given: WhatsApp webhook receives "spent 500 on groceries"
- When: Message handler is invoked
- Then: Intent is persisted to inbox **first**
- And: 200 OK is returned to webhook **immediately**
- And: Processing happens **asynchronously** after acknowledgment

#### Invariant 2: Raw Text Immutability

The `raw_text` field must **never be mutated** after creation.

- `raw_text` is the source of truth
- Processing may fail and retry - original text must be preserved
- Enrichment goes into other fields, not raw_text

Example:
- Given: Intent with `raw_text = "dinner 300"`
- When: LLM extracts amount=300, category=FOOD
- Then: `raw_text` remains "dinner 300"
- And: Extracted data goes to transaction table, not inbox

#### Invariant 3: No Financial Data in Inbox

The `user_intent_inbox` table stores **NO financial data**.

- No amounts, balances, or transactions
- No account references or container IDs
- Intent inbox is pre-financial stage

Prohibited fields in `user_intent_inbox`:
- ❌ `amount`
- ❌ `balance`
- ❌ `account_id`
- ❌ `container_id`
- ❌ `transaction_id`

#### Invariant 4: No Inline Handler Execution

Persistence to inbox must **NOT execute financial handlers**.

- Saving intent does NOT create transactions
- Saving intent does NOT adjust balances
- Saving intent does NOT call LLMs
- Processing happens separately, asynchronously

Example:
```java
// ✅ CORRECT
UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, rawText);
return ResponseEntity.ok().build(); // Immediate ack

// Later, async:
orchestrator.process(intent); // Processing happens separately

// ❌ WRONG
UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, rawText);
orchestrator.process(intent); // NO! Handler execution delays ack
return ResponseEntity.ok().build();
```

#### Invariant 5: Status Mutability Only

After creation, **only status-related fields** may be updated.

Mutable fields:
- ✅ `status`
- ✅ `status_reason`
- ✅ `detected_intent` (after classification)
- ✅ `intent_confidence` (after classification)
- ✅ `missing_fields`
- ✅ `processing_attempts`
- ✅ `last_processed_at`

Immutable fields:
- ❌ `raw_text` (NEVER changes)
- ❌ `user_id` (set at creation)
- ❌ `channel` (set at creation)
- ❌ `correlation_id` (set at creation)
- ❌ `received_at` (set at creation)

#### Invariant 6: Deduplication via Correlation ID

Each intent must have a unique `correlation_id`.

- Format: `{channel}:{userId}:{timestamp}:{uuid}`
- Prevents duplicate processing on retries
- Enables idempotent webhook handling

Example:
- Given: Webhook receives message twice due to retry
- When: Second message arrives with same correlation_id
- Then: System detects duplicate and returns existing intent
- And: No duplicate processing occurs

### Integration Test Requirements

All intent inbox implementations must pass these tests:

1. **Persistence Durability Test**
   - Intent persisted to database immediately
   - Intent retrievable after persistence
   - All fields correctly saved

2. **Immediate Acknowledgment Test**
   - Persistence completes in < 500ms
   - Status is PENDING after persistence
   - No processing has occurred

3. **Raw Text Immutability Test**
   - Raw text never changes after creation
   - Updates to other fields don't affect raw_text

4. **No Financial Data Test**
   - Inbox table has no financial columns
   - Entity has no amount/balance fields

5. **Status Mutability Test**
   - Status can transition: PENDING → PROCESSING → COMPLETED
   - Status can transition: PENDING → PROCESSING → FAILED
   - Raw text remains unchanged during transitions

6. **Deduplication Test**
   - Duplicate correlation_id detected
   - Existing intent returned on duplicate
   - No duplicate database rows created

### Usage in Code

When implementing intent ingestion:

```java
// 1. Persist immediately
UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, rawText);

// 2. Return immediate acknowledgment
return ResponseEntity.ok().build();

// 3. Process asynchronously (in @Async method)
@Async
void processLater(Long intentId) {
    UserIntentInboxEntity intent = repository.findById(intentId).orElseThrow();
    
    // Mark as processing
    intent.setStatus(IntentStatus.PROCESSING);
    repository.save(intent);
    
    try {
        // Process intent
        orchestrator.process(intent.getRawText(), context);
        
        // Mark as completed
        intent.setStatus(IntentStatus.COMPLETED);
    } catch (Exception e) {
        intent.markAsFailed(e.getMessage());
    }
    
    repository.save(intent);
}
```

---

## 7. Phase 2: Async Processing Engine Contract

**This section defines the contract for Phase 2 async intent processing.**

### Processing Engine Purpose

The `IntentProcessingEngine` processes intents from the inbox asynchronously.

- Decouples ingestion from processing
- Ensures idempotent processing (no double financial impact)
- Manages state transitions
- Handles failures and retries

### Intent State Machine

Phase 2 introduces a formal state machine for intent processing.

```
RECEIVED → PROCESSING → PROCESSED
                    ↓→ NEEDS_INPUT
                    ↓→ FAILED

NEEDS_INPUT → PROCESSING (on retry)
FAILED → PROCESSING (on retry)
```

**State Descriptions:**

- **RECEIVED**: Intent persisted, awaiting processing (Phase 1)
- **PROCESSING**: Intent currently being processed (Phase 2)
- **PROCESSED**: Intent successfully processed and complete
- **NEEDS_INPUT**: Intent requires additional user input (follow-up)
- **FAILED**: Intent processing failed (can be retried)
- **IGNORED**: Intent intentionally not processed (spam, invalid)

### Processing Engine Invariants

These rules must hold for all intent processing:

#### Invariant 1: Idempotent Processing

Financial handlers must execute **at most once** per intent.

- Retries do not double-apply financial impact
- System checks if financial impact already applied before processing
- Uses correlation_id and transaction lookups for deduplication

Example:
```java
// ✅ CORRECT - Idempotency check prevents double impact
boolean alreadyApplied = checkIfFinanciallyApplied(intent);
if (alreadyApplied) {
    log.info("Intent already has financial impact, marking PROCESSED");
    intent.markAsProcessed();
    return;
}

// Process only if not already applied
orchestrator.process(intent.getRawText(), context);
```

#### Invariant 2: State Transition Guards

State transitions must follow the state machine.

Valid transitions:
- ✅ RECEIVED → PROCESSING
- ✅ PROCESSING → PROCESSED
- ✅ PROCESSING → NEEDS_INPUT
- ✅ PROCESSING → FAILED
- ✅ NEEDS_INPUT → PROCESSING (retry)
- ✅ FAILED → PROCESSING (retry)

Invalid transitions:
- ❌ PROCESSED → PROCESSING (cannot retry completed intent)
- ❌ RECEIVED → PROCESSED (must go through PROCESSING)
- ❌ PROCESSING → RECEIVED (invalid backward transition)

Example:
```java
// Retry validation
if (intent.getStatus() != IntentStatus.FAILED && 
    intent.getStatus() != IntentStatus.NEEDS_INPUT) {
    throw new IllegalStateException(
        "Cannot retry intent in status " + intent.getStatus());
}
```

#### Invariant 3: Processing Attempt Tracking

Every processing attempt must be tracked.

- `processing_attempts` increments on each attempt
- `last_processed_at` updates on each attempt
- Enables monitoring and debugging

Example:
```java
public void markAsProcessing() {
    this.status = IntentStatus.PROCESSING;
    this.processingAttempts++;
    this.lastProcessedAt = LocalDateTime.now();
}
```

#### Invariant 4: Classification Before Processing

Intent classification happens before handler execution.

- `detected_intent` and `intent_confidence` set during first processing
- Classification failures do not block processing (defaults to UNKNOWN)
- Classification is cached for retries

Example:
- Given: Intent with `raw_text = "spent 500 on groceries"`
- When: First processing attempt
- Then: `detected_intent = "EXPENSE"` and `intent_confidence = 0.95`
- And: Classification persisted for future retries

#### Invariant 5: Async Processing Isolation

Processing happens asynchronously, isolated from ingestion.

- Ingestion returns immediately (Phase 1)
- Processing executes in separate thread pool
- Failures in processing do not affect ingestion
- Processing can retry without re-ingesting

Example:
```java
// Ingestion (fast path)
UserIntentInboxEntity intent = intentInboxService.persistIntent(userId, channel, text);
return ResponseEntity.ok().build(); // < 500ms

// Processing (slow path, async)
@Async("intentProcessingExecutor")
public void processIntent(Long intentId) {
    // This runs in background, decoupled from webhook
}
```

#### Invariant 6: Retry Safety

Retries must be safe and not cause duplicate financial impact.

- Only FAILED and NEEDS_INPUT intents can be retried
- Retry transitions intent back to RECEIVED, then to PROCESSING
- Financial impact deduplication prevents double-application
- Retry increments `processing_attempts`

Example:
```java
// Safe retry flow
Intent (FAILED) → retryIntent() → RECEIVED → processIntent() → PROCESSING
                                                              ↓
                                         (check already applied) → PROCESSED
```

### Integration Test Requirements

All intent processing implementations must pass these tests:

1. **Async Processing Test**
   - Intent transitions from RECEIVED to terminal state
   - Processing happens asynchronously
   - Terminal states: PROCESSED, NEEDS_INPUT, or FAILED

2. **Idempotent Processing Test**
   - Multiple calls to processIntent() for same intent
   - Financial impact occurs only once
   - No duplicate transactions created

3. **Retry Safety Test**
   - FAILED intent can be retried
   - NEEDS_INPUT intent can be retried
   - Retry increments processing_attempts
   - Retry does not double-apply financial impact

4. **State Transition Guards Test**
   - PROCESSED intent cannot be retried (throws exception)
   - Invalid transitions rejected

5. **Processing Attempt Tracking Test**
   - processing_attempts increments on each attempt
   - last_processed_at updates on each attempt

6. **Concurrent Processing Test**
   - Multiple concurrent processIntent() calls
   - Race condition handled gracefully
   - No duplicate financial impact

7. **Classification Test**
   - detected_intent set during processing
   - intent_confidence set during processing
   - Classification cached for retries

8. **Failure Recovery Test**
   - FAILED status has status_reason
   - Failure details captured for debugging

### Usage in Code

When implementing async processing:

```java
// Phase 1: Ingestion (WhatsAppMessageProcessor)
@Async("whatsappExecutor")
public void processIncomingMessage(String from, String text) {
    // 1. Persist (fast)
    UserIntentInboxEntity intent = intentInboxService.persistIntent(from, "WHATSAPP", text);
    
    // 2. Trigger async processing (decoupled)
    processingEngine.processIntent(intent.getId());
    
    // Webhook returns immediately
}

// Phase 2: Processing (IntentProcessingEngine)
@Async("intentProcessingExecutor")
public void processIntent(Long intentId) {
    UserIntentInboxEntity intent = repository.findById(intentId).orElseThrow();
    
    // Idempotency check
    if (intent.getStatus() == IntentStatus.PROCESSED) {
        return; // Already done
    }
    
    // Mark as processing
    intent.markAsProcessing();
    repository.save(intent);
    
    try {
        // Classify if needed
        if (intent.getDetectedIntent() == null) {
            IntentResult classification = classifier.classify(intent.getRawText());
            intent.setDetectedIntent(classification.intent());
            intent.setIntentConfidence(BigDecimal.valueOf(classification.confidence()));
        }
        
        // Check financial impact deduplication
        if (checkIfFinanciallyApplied(intent)) {
            intent.markAsProcessed();
            repository.save(intent);
            return;
        }
        
        // Process through orchestrator
        SpeechResult result = orchestrator.process(intent.getRawText(), context);
        
        // Handle result
        switch (result.getStatus()) {
            case SAVED -> intent.markAsProcessed();
            case FOLLOWUP -> intent.markAsNeedsInput("Follow-up required");
            case INVALID, UNKNOWN -> intent.markAsFailed("Processing failed");
        }
        
    } catch (Exception e) {
        intent.markAsFailed(e.getMessage());
    }
    
    repository.save(intent);
}

// Retry
public void retryIntent(Long intentId) {
    UserIntentInboxEntity intent = repository.findById(intentId).orElseThrow();
    
    // Validate can retry
    if (intent.getStatus() != IntentStatus.FAILED && 
        intent.getStatus() != IntentStatus.NEEDS_INPUT) {
        throw new IllegalStateException("Cannot retry intent in status " + intent.getStatus());
    }
    
    // Transition to RECEIVED and reprocess
    intent.setStatus(IntentStatus.RECEIVED);
    repository.save(intent);
    
    processIntent(intentId);
}
```

### Financial Impact Deduplication

The key mechanism for preventing double financial impact:

```java
private boolean checkIfFinanciallyApplied(UserIntentInboxEntity intent) {
    // Check if transaction already exists for this intent
    // Implementation options:
    
    // Option 1: Check by raw_text match
    long existingTxns = transactionRepository.findAll().stream()
        .filter(tx -> tx.getRawText() != null && 
                     tx.getRawText().equals(intent.getRawText()) &&
                     tx.isFinanciallyApplied())
        .count();
    
    // Option 2: Store correlation_id in transaction (future enhancement)
    // long existingTxns = transactionRepository
    //     .countByCorrelationIdAndFinanciallyApplied(intent.getCorrelationId(), true);
    
    return existingTxns > 0;
}
```

**Important**: This deduplication check is separate from the database-level correlation_id uniqueness constraint. The correlation_id prevents duplicate **intents**, while this check prevents duplicate **financial impact**.

### Error Handling

Processing errors are captured and retryable:

```java
try {
    // Process intent
    SpeechResult result = orchestrator.process(intent.getRawText(), context);
    handleResult(intent, result);
} catch (Exception e) {
    // Capture error details
    intent.markAsFailed(e.getMessage());
    log.error("Failed to process intent {}: {}", intentId, e.getMessage(), e);
}

repository.save(intent);
```

Failed intents can be:
- Retried manually via `retryIntent(intentId)`
- Retried automatically by scheduled job (future enhancement)
- Investigated via `status_reason` field

---

## 8. Phase 3: Async Follow-Up Handling Contract

**This section defines the contract for Phase 3 follow-up handling.**

### Follow-Up Purpose

When processing detects missing information, the system transitions the intent to NEEDS_INPUT state and awaits user response.

- Follow-ups are asynchronous (no blocking)
- Correlation is deterministic (most recent NEEDS_INPUT)
- Only one unresolved follow-up per user at a time
- New messages are accepted even with pending follow-ups

### Follow-Up Schema

Added column to `user_intent_inbox`:
- `followup_parent_id` - References parent intent (nullable, self-referencing FK with CASCADE)

Indexes:
- `idx_intent_followup_parent` - Find follow-ups by parent ID
- `idx_intent_needs_input_by_user` - Find NEEDS_INPUT intents by user

### Follow-Up Flow

```
User sends incomplete message
↓
Processing detects missing data
↓
Intent marked NEEDS_INPUT with status_reason
↓
User sends follow-up message
↓
System correlates to pending NEEDS_INPUT intent
↓
Follow-up linked via followup_parent_id
↓
Parent intent resumed (NEEDS_INPUT → RECEIVED → PROCESSING)
↓
Follow-up marked PROCESSED (incorporated into parent)
↓
Parent processing continues with combined context
↓
Parent marked PROCESSED or NEEDS_INPUT (if still incomplete)
```

### Follow-Up Invariants

These rules must hold for all follow-up handling:

#### Invariant 1: Deterministic Correlation

Follow-up messages correlate to the most recent NEEDS_INPUT intent for that user.

- Uses `findFirstByUserIdAndStatusOrderByReceivedAtDesc(userId, NEEDS_INPUT)`
- Deterministic - same inputs produce same correlation
- No ambiguity - one pending follow-up per user

Example:
```java
// User sends incomplete message
UserIntentInboxEntity intent = persistIntent("user1", "WHATSAPP", "spent 500");
// Processing marks as NEEDS_INPUT
intent.markAsNeedsInput("Missing category");

// User sends follow-up
UserIntentInboxEntity followUp = persistIntent("user1", "WHATSAPP", "groceries");
// System correlates to pending intent
Optional<UserIntentInboxEntity> pending = findPendingFollowUp("user1");
// Returns the "spent 500" intent
```

#### Invariant 2: Only One Unresolved Follow-Up Per User

At most one NEEDS_INPUT intent exists per user at any time.

- Validated by `countByUserIdAndStatus(userId, NEEDS_INPUT) <= 1`
- System invariant - should never be violated
- If violated, indicates bug in state management

Example:
```java
// Valid state
User A: NEEDS_INPUT intent (waiting for category)
User A: RECEIVED intents (queued for processing)
User A: PROCESSED intents (completed)

// Invalid state (invariant violation)
User A: NEEDS_INPUT intent 1
User A: NEEDS_INPUT intent 2  // ❌ Should never happen
```

#### Invariant 3: Async Follow-Up Processing

Follow-up correlation and resumption are asynchronous.

- No blocking in webhook request thread
- Follow-up linking happens in async @Async method
- Parent resumption triggers new async processing
- Webhook returns immediately after persistence

Example:
```java
@Async("whatsappExecutor")
public void processIncomingMessage(String from, String text) {
    // Persist (fast)
    UserIntentInboxEntity intent = persistIntent(from, "WHATSAPP", text);
    
    // Correlate (fast, deterministic)
    boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
        from, "WHATSAPP", text, intent.getId());
    
    // Returns immediately - processing happens in background
}
```

#### Invariant 4: Follow-Up Incorporation

Follow-up intents are marked PROCESSED after incorporation into parent.

- Follow-up raw_text preserved (immutability invariant)
- Follow-up status transitions: RECEIVED → PROCESSED
- Parent status transitions: NEEDS_INPUT → RECEIVED → PROCESSING
- Combined context passed to orchestrator

Example:
```java
// Before
Parent: NEEDS_INPUT (id=1, raw_text="spent 500")
FollowUp: RECEIVED (id=2, raw_text="groceries", followup_parent_id=null)

// After correlation
Parent: RECEIVED (id=1, raw_text="spent 500", status_reason="Resume with: groceries")
FollowUp: PROCESSED (id=2, raw_text="groceries", followup_parent_id=1)
```

#### Invariant 5: New Messages Accepted During Follow-Up

User can send new intents even with pending follow-up.

- Current behavior: Any message while NEEDS_INPUT exists is treated as follow-up
- Future enhancement: Intent classification could detect unrelated intents
- System remains responsive - no blocking on follow-up completion

Example:
```java
// Current Phase 3 behavior
User: "spent 500" → NEEDS_INPUT (missing category)
User: "check my balance" → Treated as follow-up to "spent 500"

// Future enhancement
User: "spent 500" → NEEDS_INPUT (missing category)
User: "check my balance" → Detected as new QUERY intent (different from EXPENSE)
```

#### Invariant 6: Follow-Up Resumability

Parent intent can be resumed multiple times if still incomplete.

- First follow-up may not provide all missing data
- Parent transitions: NEEDS_INPUT → PROCESSING → NEEDS_INPUT (if still incomplete)
- Second follow-up correlates to same parent
- Process repeats until complete or fails

Example:
```java
// Multi-turn conversation
Turn 1:
User: "spent 500"
System: NEEDS_INPUT (missing category)

Turn 2:
User: "groceries"
System: NEEDS_INPUT (missing payment method)

Turn 3:
User: "credit card"
System: PROCESSED (all data collected)
```

### Integration Test Requirements

All follow-up implementations must pass these tests:

1. **Basic Follow-Up Correlation Test**
   - NEEDS_INPUT intent exists
   - User sends follow-up
   - Correlated to correct parent
   - Follow-up linked via followup_parent_id

2. **Find Pending Follow-Up Test**
   - findPendingFollowUp returns most recent NEEDS_INPUT
   - Returns empty when no NEEDS_INPUT exists

3. **Unresolved Follow-Up Detection Test**
   - hasUnresolvedFollowUp returns true when NEEDS_INPUT exists
   - Returns false when no NEEDS_INPUT

4. **Single Pending Follow-Up Invariant Test**
   - At most one NEEDS_INPUT per user
   - Validation detects violations

5. **Out-of-Order Messages Test**
   - New intent while follow-up pending
   - System handles deterministically

6. **Follow-Up Linking Test**
   - followup_parent_id correctly set
   - isFollowup() returns true
   - Find follow-ups by parent works

7. **Multiple Follow-Ups Test**
   - Parent can have multiple sequential follow-ups
   - Each follow-up processed correctly

8. **Duplicate Follow-Up Replies Test**
   - Duplicate messages handled gracefully
   - No duplicate processing

9. **Different Users Test**
   - Each user's follow-ups independent
   - No cross-contamination

### Usage in Code

When implementing follow-up handling:

```java
// Phase 3: WhatsAppMessageProcessor with follow-up support
@Async("whatsappExecutor")
public void processIncomingMessage(String from, String text) {
    // Phase 1: Persist
    UserIntentInboxEntity intent = intentInboxService.persistIntent(from, "WHATSAPP", text);
    
    // Phase 3: Check for follow-up
    boolean isFollowUp = followUpCorrelationService.processAsFollowUpIfApplicable(
            from, "WHATSAPP", text, intent.getId());
    
    if (isFollowUp) {
        // Follow-up correlation service handles resumption
        return;
    }
    
    // Phase 2: Process new intent
    processingEngine.processIntent(intent.getId());
}

// FollowUpCorrelationService
@Transactional
public boolean processAsFollowUpIfApplicable(String userId, String channel, String rawText, Long newIntentId) {
    // Find pending follow-up
    Optional<UserIntentInboxEntity> pendingIntent = findPendingFollowUp(userId);
    
    if (pendingIntent.isEmpty()) {
        return false; // No pending follow-up, treat as new intent
    }
    
    // Link follow-up to parent
    UserIntentInboxEntity parent = pendingIntent.get();
    UserIntentInboxEntity followUp = intentRepository.findById(newIntentId).orElseThrow();
    followUp.setFollowupParentId(parent.getId());
    intentRepository.save(followUp);
    
    // Resume parent processing
    resumeParentIntentProcessing(parent, followUp);
    
    return true;
}

@Transactional
public void resumeParentIntentProcessing(UserIntentInboxEntity parent, UserIntentInboxEntity followUp) {
    // Transition parent back to RECEIVED
    parent.setStatus(IntentStatus.RECEIVED);
    parent.setStatusReason("Resume with follow-up: " + followUp.getRawText());
    intentRepository.save(parent);
    
    // Mark follow-up as PROCESSED (incorporated)
    followUp.markAsProcessed();
    followUp.setStatusReason("Incorporated into parent intent " + parent.getId());
    intentRepository.save(followUp);
    
    // Trigger async reprocessing
    processingEngine.processIntent(parent.getId());
}
```

### Follow-Up Behavior Guarantees

**Determinism**: Same user state + same message → same correlation

**Async**: No blocking - all operations async

**Resumability**: Parent can be resumed multiple times

**Isolation**: Per-user follow-up state isolated

**Acceptance**: New messages always accepted (never blocked)

### Future Enhancements

**Intent Classification for Follow-Ups**:
- Detect when message is unrelated to pending follow-up
- Allow user to send new intent while follow-up pending
- Requires LLM context understanding

**Multi-Intent Follow-Ups**:
- Support follow-ups for multiple pending intents
- User selects which intent to continue
- Requires conversation context management

**Follow-Up Timeout**:
- Auto-expire NEEDS_INPUT after timeout (e.g., 1 hour)
- Mark as FAILED with reason "Timeout waiting for user input"
- Scheduled job to clean up stale follow-ups

---

## 9. Phase 4: Channel Independence Rule

**This section defines the contract for channel-independent processing.**

### Channel Independence Purpose

The core intent processing engine is completely independent of the channel (WhatsApp, UI, Mobile, API).

- Same intent behaves identically across all channels
- Core processing has zero channel dependencies
- Channel-specific concerns (replies, notifications) handled separately
- Enables consistent user experience across all channels

### Channel Types

**Message-Based Channels** (e.g., WhatsApp):
- User sends messages via external platform
- Messages arrive via webhook
- Processing happens asynchronously
- Responses sent via channel-specific API

**State-Based Channels** (e.g., UI, Mobile):
- User interacts via application UI
- Client polls for pending actions
- User provides responses via API calls
- UI displays current state

### Channel-Agnostic Architecture

```
┌─────────────┐       ┌──────────────────┐       ┌─────────────────┐
│  WhatsApp   │──────▶│ Channel-Agnostic │──────▶│ Intent          │
│  Webhook    │       │ Intent Service   │       │ Processing      │
└─────────────┘       │                  │       │ Engine          │
                      │                  │       └─────────────────┘
┌─────────────┐       │                  │              │
│  UI/Mobile  │──────▶│  ingestIntent()  │              │
│  API        │       │  getPending()    │              │
└─────────────┘       │  resumeWith()    │              │
                      └──────────────────┘              │
                                                        ▼
                                              ┌─────────────────┐
                                              │ Handler         │
                                              │ Execution       │
                                              │ (Channel-Free)  │
                                              └─────────────────┘
```

### Channel Independence Invariants

#### Invariant 1: Core Processing is Channel-Agnostic

All core processing logic has zero dependency on channel.

- Intent classification independent of channel
- Handler execution independent of channel
- State transitions independent of channel
- Financial operations independent of channel

Example:
```java
// ❌ WRONG - Channel-dependent logic
public void processIntent(UserIntentInboxEntity intent) {
    if (intent.getChannel().equals("WHATSAPP")) {
        // WhatsApp-specific processing
    } else if (intent.getChannel().equals("WEB")) {
        // Web-specific processing
    }
}

// ✅ CORRECT - Channel-agnostic logic
public void processIntent(UserIntentInboxEntity intent) {
    // Same logic regardless of channel
    IntentResult classification = classifier.classify(intent.getRawText());
    SpeechResult result = orchestrator.process(intent.getRawText(), context);
    // Channel-independent processing
}
```

#### Invariant 2: Same Intent Behaves Identically

Given the same intent text, processing produces the same result regardless of channel.

- Classification is deterministic (given same LLM state)
- Handlers produce same financial impact
- State transitions follow same rules
- Validation is consistent

Example:
```java
// Intent via WhatsApp
UserIntentInboxEntity whatsappIntent = ingestIntent("user1", "WHATSAPP", "spent 500 on groceries");
// Intent via Web
UserIntentInboxEntity webIntent = ingestIntent("user1", "WEB", "spent 500 on groceries");

// Both should:
// - Be classified as EXPENSE
// - Create same transaction amount
// - Follow same validation rules
// - Have same completion criteria
```

#### Invariant 3: Channel Stored for Context Only

The `channel` field is stored for:
- Auditing (know where intent came from)
- Analytics (channel usage patterns)
- Correlation ID generation (uniqueness per channel)

But NOT for:
- Processing logic decisions
- Handler selection
- Validation rules
- Business logic

#### Invariant 4: Separate Channel-Specific Concerns

Channel-specific logic is isolated to channel adapters:

**Separated Concerns**:
- ✅ Reply sending (WhatsAppReplySender for WhatsApp)
- ✅ Notification delivery (push notifications for Mobile)
- ✅ UI updates (polling/SSE for Web)
- ✅ Authentication (channel-specific auth)

**Channel-Agnostic Concerns**:
- ✅ Intent ingestion (ChannelAgnosticIntentService)
- ✅ Intent processing (IntentProcessingEngine)
- ✅ Follow-up correlation (FollowUpCorrelationService)
- ✅ Handler execution (SpeechOrchestrator)

### API Endpoints for UI/Mobile

**POST /api/v1/intents** - Submit new intent
```json
Request:
{
  "userId": "user123",
  "channel": "WEB",
  "text": "spent 500 on groceries"
}

Response:
{
  "intentId": 42,
  "correlationId": "WEB:user123:1234567890:abc123",
  "status": "RECEIVED",
  "message": "Intent submitted successfully"
}
```

**GET /api/v1/intents/pending?userId=user123** - Get pending action
```json
Response (has pending):
{
  "hasPendingAction": true,
  "intentId": 42,
  "originalText": "spent 500",
  "statusReason": "Missing category",
  "detectedIntent": "EXPENSE"
}

Response (no pending):
{
  "hasPendingAction": false
}
```

**POST /api/v1/intents/resume** - Resume with response
```json
Request:
{
  "userId": "user123",
  "channel": "WEB",
  "responseText": "groceries"
}

Response:
{
  "intentId": 43,
  "correlationId": "WEB:user123:1234567891:def456",
  "status": "PROCESSED",
  "message": "Resume request processed successfully"
}
```

### Integration Test Requirements

All channel independence implementations must pass these tests:

1. **Submit Intent via API Test**
   - UI client can submit intents
   - Intent created with correct channel
   - Status is RECEIVED

2. **Get Pending Action Test**
   - Returns null when no pending action
   - Returns pending NEEDS_INPUT intent when exists
   - Contains original text and status reason

3. **Resume Intent Test**
   - User can resume with response
   - Follow-up linked to parent intent
   - Parent processing resumed

4. **Channel Independence Test**
   - Same intent text from different channels
   - Both processed identically
   - Same classification and validation

5. **Default Channel Test**
   - Missing channel defaults to WEB
   - System remains functional

6. **Multiple Channels for Same User Test**
   - User can use multiple channels
   - Each intent tracked separately
   - No cross-contamination

### Usage in Code

When implementing channel adapters:

```java
// WhatsApp Adapter
@Service
public class WhatsAppMessageProcessor {
    private final ChannelAgnosticIntentService intentService;
    private final WhatsAppReplySender replySender; // Channel-specific
    
    @Async
    public void processIncomingMessage(String from, String text) {
        // Use channel-agnostic service
        intentService.ingestIntent(from, "WHATSAPP", text);
        
        // WhatsApp-specific reply handling happens separately
        // (via notification service when processing completes)
    }
}

// UI/Mobile Adapter
@RestController
public class IntentApiController {
    private final ChannelAgnosticIntentService intentService;
    
    @PostMapping("/api/v1/intents")
    public ResponseEntity<IntentResponse> submitIntent(@RequestBody IntentRequest request) {
        // Use same channel-agnostic service
        UserIntentInboxEntity intent = intentService.ingestIntent(
                request.getUserId(), 
                request.getChannel(), 
                request.getText()
        );
        
        // Return state for UI to display
        return ResponseEntity.ok(toResponse(intent));
    }
    
    @GetMapping("/api/v1/intents/pending")
    public ResponseEntity<PendingActionResponse> getPendingAction(@RequestParam String userId) {
        // UI can poll for pending actions
        UserIntentInboxEntity pending = intentService.getPendingAction(userId);
        return ResponseEntity.ok(toResponse(pending));
    }
}

// Core Processing (Channel-Agnostic)
@Service
public class IntentProcessingEngine {
    // NO channel dependencies
    // Same logic for all channels
    
    public void processIntent(Long intentId) {
        UserIntentInboxEntity intent = repository.findById(intentId).orElseThrow();
        
        // Channel-agnostic processing
        IntentResult classification = classifier.classify(intent.getRawText());
        SpeechResult result = orchestrator.process(intent.getRawText(), context);
        
        // No channel-specific logic here
    }
}
```

### Channel Comparison

| Feature | WhatsApp (Message-Based) | UI/Mobile (State-Based) |
|---------|-------------------------|-------------------------|
| Entry Point | Webhook | REST API |
| Interaction | Push messages | Poll + Request/Response |
| Follow-ups | Automatic via message flow | Explicit via API calls |
| State Query | N/A (messages only) | GET /pending endpoint |
| User Experience | Conversational | Form-based |
| Core Processing | ✅ Same | ✅ Same |
| Handlers | ✅ Same | ✅ Same |
| Validation | ✅ Same | ✅ Same |

### Benefits of Channel Independence

**Consistency**: Same intent processed identically across all channels

**Maintainability**: Single processing engine, not per-channel implementations

**Testability**: Test once, works everywhere

**Scalability**: Add new channels without changing core

**Flexibility**: Users can switch channels seamlessly

---

## 10. Phase 5: Edge Case Hardening

**This section documents edge case handling and system resilience.**

### Edge Case Testing Purpose

Verify system behavior under abnormal conditions:
- Duplicate messages
- Delayed replies
- Partial failures
- Race conditions
- Boundary conditions
- High load scenarios

All edge cases documented in Section 4 are covered by integration tests.

### Edge Cases Tested

#### 1. Duplicate Message Handling

**Scenario**: Same message submitted multiple times

**Behavior**:
- Each submission gets unique correlation ID (includes timestamp)
- Creates separate intents (by design - timestamps differ)
- System handles concurrent duplicates without errors
- Processing is idempotent (retries don't duplicate financial impact)

**Tests**:
- `testDuplicateMessage_SameCorrelationId` - Sequential duplicates
- `testDuplicateMessage_ConcurrentSubmission` - Concurrent duplicates
- `testDuplicateProcessing_IdempotentRetry` - Processing idempotency

**Example**:
```java
// User submits same message twice
UserIntentInboxEntity first = persistIntent("user1", "WHATSAPP", "spent 500");
UserIntentInboxEntity second = persistIntent("user1", "WHATSAPP", "spent 500");

// Different intents (different timestamps in correlation ID)
assertNotEquals(first.getId(), second.getId());

// But both processed correctly without duplication
```

#### 2. Delayed Reply Handling

**Scenario**: User responds to NEEDS_INPUT intent after long delay

**Behavior**:
- System correlates to most recent NEEDS_INPUT intent
- No timeout enforcement (currently)
- Works even with hours/days delay
- Future enhancement: Add timeout to ignore very old pending intents

**Tests**:
- `testDelayedReply_OldPendingIntent` - 2 hour delay
- `testDelayedReply_NewIntentAfterTimeout` - 2 day delay

**Example**:
```java
// User has NEEDS_INPUT intent from 2 hours ago
UserIntentInboxEntity oldIntent = createNeedsInput("user1", "spent 500");
oldIntent.setReceivedAt(LocalDateTime.now().minusHours(2));

// User finally responds
UserIntentInboxEntity reply = persistIntent("user1", "WHATSAPP", "groceries");

// Still correlates correctly
assertTrue(isFollowUp(reply));
assertEquals(oldIntent.getId(), reply.getFollowupParentId());
```

#### 3. Partial Failure Handling

**Scenario**: Processing fails midway through

**Behavior**:
- Intent marked FAILED with reason
- Processing attempts incremented
- Can be retried via `retryIntent()`
- Retry does not duplicate financial impact

**Tests**:
- `testPartialFailure_ProcessingFailsMidway` - Mid-processing failure
- `testPartialFailure_DatabaseConstraintViolation` - Database errors

**Example**:
```java
// Intent fails during processing
processIntent(intentId);

// Marked as FAILED
assertEquals(IntentStatus.FAILED, intent.getStatus());
assertNotNull(intent.getStatusReason());

// Can retry safely
retryIntent(intentId);

// No duplicate financial impact (idempotency check)
```

#### 4. Race Condition Handling

**Scenario**: Concurrent operations on same intent

**Behavior**:
- Concurrent follow-up responses handled gracefully
- Processing and follow-up checks don't conflict
- Database-level constraints prevent corruption
- System remains consistent

**Tests**:
- `testRaceCondition_ConcurrentFollowUpResponses` - Concurrent follow-ups
- `testRaceCondition_ProcessWhileIngestingFollowUp` - Process during follow-up check

**Example**:
```java
// User sends two follow-up responses concurrently
CompletableFuture.runAsync(() -> submitFollowUp("groceries"));
CompletableFuture.runAsync(() -> submitFollowUp("groceries"));

// Both complete without errors
// Parent resumed correctly
```

#### 5. Ordering Issue Handling

**Scenario**: Messages arrive out of order or in quick succession

**Behavior**:
- All messages persisted with correct receive timestamps
- Processing order determined by async queue
- Follow-up only works when parent in NEEDS_INPUT state
- Multiple rapid messages all processed

**Tests**:
- `testOrdering_MultipleIntentsFromSameUser` - Quick succession
- `testOrdering_FollowUpBeforeParentProcessed` - Out-of-order timing

**Example**:
```java
// User sends 3 messages quickly
ingestIntent("user1", "WHATSAPP", "message 1");
ingestIntent("user1", "WHATSAPP", "message 2");
ingestIntent("user1", "WHATSAPP", "message 3");

// All persisted with correct ordering
List<Intent> intents = findByUser("user1");
assertTrue(intents.get(0).getReceivedAt().isBefore(intents.get(1).getReceivedAt()));
```

#### 6. Boundary Condition Handling

**Scenario**: Edge cases for input validation

**Behavior**:
- Empty messages accepted (raw text preserved as empty string)
- Very long user IDs handled (database constraints may apply)
- Special characters preserved (emojis, @mentions, #tags)
- System degrades gracefully for constraint violations

**Tests**:
- `testBoundary_EmptyMessage` - Empty text
- `testBoundary_VeryLongUserId` - 200+ character user ID
- `testBoundary_SpecialCharactersInText` - Unicode, emojis

**Example**:
```java
// Empty message
UserIntentInboxEntity empty = persistIntent("user1", "WHATSAPP", "");
assertEquals("", empty.getRawText()); // Preserved

// Special characters
UserIntentInboxEntity special = persistIntent("user1", "WHATSAPP", "🍔 #dinner @john");
assertEquals("🍔 #dinner @john", special.getRawText()); // Preserved
```

#### 7. High Load Handling

**Scenario**: Many intents submitted in short time

**Behavior**:
- All intents persisted successfully
- Async processing queue handles load
- No dropped messages
- System remains responsive

**Tests**:
- `testStress_ManyIntentsQuickly` - 20 intents rapidly

**Example**:
```java
// Submit 20 intents quickly
for (int i = 0; i < 20; i++) {
    ingestIntent("user1", "WHATSAPP", "message " + i);
}

// All persisted
assertEquals(20, countUserIntents("user1"));
```

### Edge Case Invariants

These rules must hold for all edge cases:

#### Invariant 1: No Data Loss

Under any edge case, raw user input is never lost.

- Duplicate submissions create separate intents (different timestamps)
- Failed processing preserves raw text
- Database errors don't lose user data
- Retries don't mutate original raw text

#### Invariant 2: Idempotent Retries

Processing retries never cause duplicate financial impact.

- Already-processed intents skip handler execution
- Financial impact check prevents double-application
- Retry increments attempts but doesn't duplicate work
- Safe to retry any FAILED or NEEDS_INPUT intent

#### Invariant 3: Graceful Degradation

System handles errors without crashing.

- Invalid inputs logged and handled
- Database constraints cause controlled failures
- Partial failures captured in status_reason
- Race conditions don't corrupt state

#### Invariant 4: Deterministic Behavior

Same inputs produce same outputs (given same system state).

- Classification deterministic (given same LLM state)
- Follow-up correlation deterministic (most recent NEEDS_INPUT)
- State transitions follow rules consistently
- No random behavior in core processing

### Integration Test Requirements

All edge case implementations must pass these tests:

1. **Duplicate Message Tests** (3 tests)
   - Sequential duplicates handled
   - Concurrent duplicates handled
   - Idempotent retry processing

2. **Delayed Reply Tests** (2 tests)
   - Old pending intents still work
   - Very old pending intents behavior documented

3. **Partial Failure Tests** (2 tests)
   - Mid-processing failures recoverable
   - Database constraint violations handled

4. **Race Condition Tests** (2 tests)
   - Concurrent follow-ups safe
   - Processing during follow-up check safe

5. **Ordering Issue Tests** (2 tests)
   - Multiple quick messages ordered correctly
   - Follow-up timing edge cases handled

6. **Boundary Condition Tests** (3 tests)
   - Empty messages handled
   - Very long user IDs handled
   - Special characters preserved

7. **High Load Tests** (1 test)
   - Many rapid intents handled

### Fuzz Testing

Fuzz testing runs random scenarios to discover unexpected edge cases.

**Current Coverage**:
- Financial simulation fuzz testing (existing)
- 50 iterations per test run
- 100 iterations in CI/CD
- All failures reproducible via seed

**Intent Processing Fuzz** (Future Enhancement):
- Random intent sequences
- Random timing and ordering
- Random follow-up patterns
- Stress test async processing

**To run fuzz tests**:
```bash
mvn verify -Pintegration -Dfuzz.iterations=50
```

**To reproduce failure**:
```bash
mvn verify -Pintegration -Dtest=FuzzSimulationIT#testReproduceSeed -Dfuzz.seed=1042
```

### Edge Cases Discovered During Testing

This section documents any NEW edge cases discovered during Phase 5.

**None discovered yet** - All tested scenarios behaved as expected per documented invariants.

If new edge cases are discovered:
1. Document behavior here first
2. Add test to verify behavior
3. Update code if needed to handle edge case
4. Never weaken existing invariants

---

## 11. Phase 6: UI/Mobile Readiness

**This section documents REST API contracts for UI/Mobile clients.**

### API Purpose

Phase 6 finalizes REST API contracts with pagination, ordering, and comprehensive error handling for UI and mobile applications.

**Design Principles**:
- No UI assumptions in backend
- No WebSocket dependency
- State-based interaction (different from WhatsApp's message-based model)
- Pagination for all list endpoints
- Comprehensive error responses

### REST API Endpoints

#### 1. Submit Intent

**POST `/api/v1/intents`**

Submit a new user intent for processing.

**Request**:
```json
{
  "userId": "user123",
  "channel": "WEB",
  "text": "spent 500 on groceries"
}
```

**Response** (200 OK):
```json
{
  "intentId": 42,
  "correlationId": "WEB:user123:1234567890:abc123",
  "status": "RECEIVED",
  "message": "Intent submitted successfully"
}
```

**Error Response** (400 Bad Request):
```json
{
  "message": "userId is required"
}
```

**Validation**:
- `userId` is required (not blank)
- `text` is required (not blank)
- `channel` defaults to "WEB" if not provided

#### 2. Get Pending Action

**GET `/api/v1/intents/pending?userId=user123`**

Query for pending NEEDS_INPUT intent requiring user follow-up.

**Response** (200 OK) - With pending action:
```json
{
  "hasPendingAction": true,
  "intentId": 42,
  "originalText": "spent 500",
  "statusReason": "Missing category",
  "detectedIntent": "EXPENSE",
  "receivedAt": "2025-12-30T10:00:00"
}
```

**Response** (200 OK) - No pending action:
```json
{
  "hasPendingAction": false
}
```

**Use Case**: UI polls this endpoint to check if user needs to provide additional information.

#### 3. Resume Intent

**POST `/api/v1/intents/resume`**

Resume processing with user's follow-up response.

**Request**:
```json
{
  "userId": "user123",
  "channel": "WEB",
  "responseText": "groceries"
}
```

**Response** (200 OK):
```json
{
  "intentId": 43,
  "correlationId": "WEB:user123:1234567891:def456",
  "status": "PROCESSED",
  "message": "Resume request processed successfully"
}
```

**Behavior**:
- Links follow-up to pending NEEDS_INPUT intent
- Resumes parent intent processing
- Returns new follow-up intent details

#### 4. Get Intent History

**GET `/api/v1/intents/history?userId=user123&page=0&size=20&sortBy=receivedAt&sortDirection=DESC&status=PROCESSED`**

Query user's intent history with pagination and filtering.

**Parameters**:
- `userId` (required) - User identifier
- `page` (optional, default: 0) - Page number (0-indexed)
- `size` (optional, default: 20, max: 100) - Page size
- `sortBy` (optional, default: "receivedAt") - Sort field
- `sortDirection` (optional, default: "DESC") - Sort direction (ASC/DESC)
- `status` (optional) - Filter by intent status (RECEIVED, PROCESSING, PROCESSED, NEEDS_INPUT, FAILED, IGNORED)

**Response** (200 OK):
```json
{
  "items": [
    {
      "intentId": 42,
      "correlationId": "WEB:user123:1234567890:abc123",
      "rawText": "spent 500 on groceries",
      "status": "PROCESSED",
      "detectedIntent": "EXPENSE",
      "intentConfidence": 0.95,
      "statusReason": null,
      "receivedAt": "2025-12-30T10:00:00",
      "lastProcessedAt": "2025-12-30T10:00:05",
      "processingAttempts": 1,
      "channel": "WEB"
    }
  ],
  "totalItems": 150,
  "totalPages": 8,
  "currentPage": 0,
  "pageSize": 20,
  "hasNext": true,
  "hasPrevious": false
}
```

**Pagination Details**:
- Results ordered by specified field and direction
- Page size limited to 100 max to prevent abuse
- Invalid status filter ignored (returns all statuses)

### API Usage Examples

#### Complete UI Workflow

```javascript
// 1. Submit intent
const submitResponse = await fetch('/api/v1/intents', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    userId: 'user123',
    channel: 'WEB',
    text: 'spent 500 on food'
  })
});
const { intentId, status } = await submitResponse.json();

// 2. Poll for pending action
const checkPending = async () => {
  const response = await fetch(`/api/v1/intents/pending?userId=user123`);
  const pending = await response.json();
  
  if (pending.hasPendingAction) {
    // Display prompt: "Please specify category for: spent 500 on food"
    return pending;
  }
  return null;
};

// 3. Resume with response
const resumeResponse = await fetch('/api/v1/intents/resume', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    userId: 'user123',
    channel: 'WEB',
    responseText: 'groceries'
  })
});

// 4. Display intent history
const historyResponse = await fetch(
  `/api/v1/intents/history?userId=user123&page=0&size=20`
);
const history = await historyResponse.json();
// Display history.items in UI table
```

#### Pagination Example

```javascript
// Fetch first page
const page1 = await fetch(
  `/api/v1/intents/history?userId=user123&page=0&size=10&sortBy=receivedAt&sortDirection=DESC`
).then(r => r.json());

// Check if more pages available
if (page1.hasNext) {
  // Fetch next page
  const page2 = await fetch(
    `/api/v1/intents/history?userId=user123&page=1&size=10&sortBy=receivedAt&sortDirection=DESC`
  ).then(r => r.json());
}

// Filter by status
const processedOnly = await fetch(
  `/api/v1/intents/history?userId=user123&status=PROCESSED`
).then(r => r.json());
```

### Integration Test Requirements

Phase 6 implementations must pass these tests:

1. **Pagination Tests** (5 tests)
   - First page returns correct items
   - Middle page has both previous and next
   - Last page has no next
   - Empty results handled
   - Max size limit enforced (100 max)

2. **Ordering Tests** (2 tests)
   - Descending order (newest first)
   - Ascending order (oldest first)

3. **Status Filter Tests** (2 tests)
   - Valid status filter applied
   - Invalid status filter ignored

4. **Validation Tests** (2 tests)
   - Missing userId returns 400
   - Missing text returns 400

5. **Error Handling Tests** (2 tests)
   - Valid submission succeeds
   - Resume without pending creates new intent

6. **Complete Workflow Test** (1 test)
   - Submit → Check history → Simulate NEEDS_INPUT → Check pending → Resume → Check history again

**Total**: 14 integration tests for Phase 6

### API Invariants

#### Invariant 1: Pagination Consistency

Pagination must remain consistent across page requests.

- Same query parameters → same total count
- Page boundaries do not overlap
- All items retrievable via pagination
- Page size never exceeds 100

#### Invariant 2: Error Response Format

All errors return consistent format with descriptive messages.

- 400 for validation errors (missing required fields)
- 500 for server errors
- Error messages human-readable
- No stack traces exposed to client

#### Invariant 3: Parameter Validation

Invalid parameters handled gracefully.

- Negative page numbers default to 0
- Page size < 1 defaults to 20
- Page size > 100 limited to 100
- Invalid status filter ignored (returns all)
- Invalid sort direction defaults to DESC

#### Invariant 4: No UI Assumptions

Backend APIs are UI-agnostic.

- No WebSocket requirements
- No session state
- Pure REST (stateless)
- Works for web, mobile, and future clients

### Channel Comparison

| Feature | WhatsApp | UI/Mobile |
|---------|----------|-----------|
| **Interaction Model** | Message-based | State-based |
| **Submit Intent** | Webhook → async | POST /api/v1/intents |
| **Check Pending** | N/A (push via WhatsApp) | GET /api/v1/intents/pending |
| **Resume** | New message → webhook | POST /api/v1/intents/resume |
| **History** | N/A | GET /api/v1/intents/history |
| **Pagination** | N/A | Full support |
| **Ordering** | N/A | Customizable |

### Future Enhancements

Potential Phase 6+ improvements:

**Filtering**:
- Date range filters
- Channel filters
- Confidence threshold filters

**Advanced Ordering**:
- Multi-field sorting
- Custom sort orders

**Bulk Operations**:
- Batch intent submission
- Bulk status updates

**Real-time Updates** (without WebSockets):
- Server-Sent Events (SSE) for pending actions
- Long polling for status changes

**Caching**:
- Response caching for history queries
- ETag support for conditional requests

---

## Summary

**Financial rules are the contract.**

- Defined in this document
- Enforced by integration tests
- Implemented by production code
- Verified by CI/CD

**Documents > Tests > Code**

When in doubt, trust this document.