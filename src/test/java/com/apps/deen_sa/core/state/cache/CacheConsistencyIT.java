package com.apps.deen_sa.core.state.cache;

import com.apps.deen_sa.IntegrationTestBase;
import com.apps.deen_sa.conversation.ConversationContext;
import com.apps.deen_sa.core.state.StateContainerEntity;
import com.apps.deen_sa.core.state.StateContainerRepository;
import com.apps.deen_sa.core.state.StateContainerService;
import com.apps.deen_sa.finance.account.AccountSetupHandler;
import com.apps.deen_sa.finance.expense.ExpenseHandler;
import com.apps.deen_sa.finance.payment.LiabilityPaymentHandler;
import com.apps.deen_sa.simulation.LLMTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify cache consistency for state containers.
 * 
 * This test specifically addresses the bug where:
 * - Bank account balances update correctly in memory after expenses
 * - Credit card outstanding does NOT update correctly after PAYMENT
 * - Database value is correct, but in-memory cache returns stale data
 */
@Import(LLMTestConfiguration.class)
public class CacheConsistencyIT extends IntegrationTestBase {

    @Autowired
    AccountSetupHandler accountSetupHandler;

    @Autowired
    ExpenseHandler expenseHandler;

    @Autowired
    LiabilityPaymentHandler liabilityPaymentHandler;

    @Autowired
    StateContainerService stateContainerService;

    @Autowired
    StateContainerRepository stateContainerRepository;

    @Autowired
    StateContainerCache cache;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setup() {
        // Do NOT clear cache - let it work naturally
        // The test will verify that cache is updated after mutations
    }

    /**
     * COMPREHENSIVE SCENARIO:
     * 1. Create BANK_ACCOUNT and CREDIT_CARD
     * 2. Perform multiple credit card expenses
     * 3. Perform PARTIAL credit card payment from bank
     * 4. Make bank account expense  
     * 5. Perform more credit card operations
     * 6. Verify cache consistency throughout
     * 
     * This test ensures that ALL container value changes (asset and liability)
     * are correctly reflected in the in-memory cache.
     */
    @Test
    void testCacheConsistencyAcrossMultipleOperations() {
        ConversationContext ctx = new ConversationContext();
        LocalDate today = LocalDate.now();

        // === SETUP ===
        accountSetupHandler.handleSpeech(
            "SIM:ACCOUNT;type=BANK_ACCOUNT;name=My Bank;current=100000;currency=INR;date=" + today,
            ctx
        );

        accountSetupHandler.handleSpeech(
            "SIM:ACCOUNT;type=CREDIT_CARD;name=My Card;current=0;limit=50000;dueDay=15;currency=INR;date=" + today,
            ctx
        );

        // === PHASE 1: Credit Card Expenses ===
        expenseHandler.handleSpeech(
            "SIM:EXPENSE;amount=5000;desc=groceries;source=CREDIT_CARD;date=" + today,
            ctx
        );

        expenseHandler.handleSpeech(
            "SIM:EXPENSE;amount=3000;desc=fuel;source=CREDIT_CARD;date=" + today,
            ctx
        );

        expenseHandler.handleSpeech(
            "SIM:EXPENSE;amount=2000;desc=dining;source=CREDIT_CARD;date=" + today,
            ctx
        );

        // Verify: Credit card outstanding = 10,000, Bank = 100,000
        assertContainerValues(10000, 100000, "After credit card expenses");

        // === PHASE 2: Credit Card Payment ===
        liabilityPaymentHandler.handleSpeech(
            "SIM:PAYMENT;amount=6000;target=CREDIT_CARD;targetName=My Card;date=" + today + ";source=BANK_ACCOUNT",
            ctx
        );

        // Verify: Credit card outstanding = 4,000, Bank = 94,000
        assertContainerValues(4000, 94000, "After credit card payment");

        // === PHASE 3: Bank Account Expense ===
        expenseHandler.handleSpeech(
            "SIM:EXPENSE;amount=5000;desc=rent;source=BANK_ACCOUNT;date=" + today,
            ctx
        );

        // Verify: Credit card = 4,000, Bank = 89,000
        assertContainerValues(4000, 89000, "After bank expense");

        // === PHASE 4: More Credit Card Activity ===
        expenseHandler.handleSpeech(
            "SIM:EXPENSE;amount=1500;desc=shopping;source=CREDIT_CARD;date=" + today,
            ctx
        );

        liabilityPaymentHandler.handleSpeech(
            "SIM:PAYMENT;amount=2500;target=CREDIT_CARD;targetName=My Card;date=" + today + ";source=BANK_ACCOUNT",
            ctx
        );

        // Verify: Credit card = 3,000 (4000 + 1500 - 2500), Bank = 86,500 (89000 - 2500)
        assertContainerValues(3000, 86500, "After final operations");
    }

    /**
     * Helper method to assert both cache and DB have the same values.
     */
    private void assertContainerValues(long expectedCreditCard, long expectedBank, String phase) {
        // Fetch via cache
        List<StateContainerEntity> cachedContainers = stateContainerService.getActiveContainers(USER_ID);

        StateContainerEntity cachedCreditCard = cachedContainers.stream()
            .filter(c -> "CREDIT_CARD".equals(c.getContainerType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Credit card not found in cache at: " + phase));

        StateContainerEntity cachedBank = cachedContainers.stream()
            .filter(c -> "BANK_ACCOUNT".equals(c.getContainerType()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Bank account not found in cache at: " + phase));

        // Fetch directly from DB
        StateContainerEntity dbCreditCard = stateContainerRepository.findById(cachedCreditCard.getId())
            .orElseThrow(() -> new AssertionError("Credit card not found in DB at: " + phase));

        StateContainerEntity dbBank = stateContainerRepository.findById(cachedBank.getId())
            .orElseThrow(() -> new AssertionError("Bank account not found in DB at: " + phase));

        // Assert cache matches DB
        assertEquals(
            0,
            dbCreditCard.getCurrentValue().compareTo(cachedCreditCard.getCurrentValue()),
            phase + ": Cache and DB must return same credit card outstanding value. " +
            "Cache=" + cachedCreditCard.getCurrentValue() + ", DB=" + dbCreditCard.getCurrentValue()
        );

        assertEquals(
            0,
            dbBank.getCurrentValue().compareTo(cachedBank.getCurrentValue()),
            phase + ": Cache and DB must return same bank account balance. " +
            "Cache=" + cachedBank.getCurrentValue() + ", DB=" + dbBank.getCurrentValue()
        );

        // Assert expected values
        assertEquals(
            0,
            new BigDecimal(expectedCreditCard).compareTo(cachedCreditCard.getCurrentValue()),
            phase + ": Credit card outstanding should be " + expectedCreditCard
        );

        assertEquals(
            0,
            new BigDecimal(expectedBank).compareTo(cachedBank.getCurrentValue()),
            phase + ": Bank account balance should be " + expectedBank
        );
    }
}
