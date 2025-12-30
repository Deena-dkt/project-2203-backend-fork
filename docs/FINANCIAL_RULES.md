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

## Summary

**Financial rules are the contract.**

- Defined in this document
- Enforced by integration tests
- Implemented by production code
- Verified by CI/CD

**Documents > Tests > Code**

When in doubt, trust this document.