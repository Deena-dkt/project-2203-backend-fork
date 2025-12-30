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

## Summary

**Financial rules are the contract.**

- Defined in this document
- Enforced by integration tests
- Implemented by production code
- Verified by CI/CD

**Documents > Tests > Code**

When in doubt, trust this document.