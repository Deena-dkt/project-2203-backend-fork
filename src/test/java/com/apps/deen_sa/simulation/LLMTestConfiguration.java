package com.apps.deen_sa.simulation;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.llm.impl.LiabilityPaymentClassifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDate;

@TestConfiguration
public class LLMTestConfiguration {

    @Bean
    @Primary
    public AccountSetupClassifier accountSetupClassifier() {
        return new AccountSetupClassifier(null) {
            @Override
            public AccountSetupDto extractAccount(String text) {
                // Expect SIM:ACCOUNT;type=...;name=...;current=...;date=YYYY-MM-DD
                AccountSetupDto dto = new AccountSetupDto();
                dto.setValid(true);

                String[] parts = text.split(";");
                for (String p : parts) {
                    if (p.startsWith("type=")) dto.setContainerType(p.substring(5));
                    if (p.startsWith("name=")) dto.setName(p.substring(5));
                    if (p.startsWith("current=")) dto.setCurrentValue(new BigDecimal(p.substring(8)));
                }
                return dto;
            }
        };
    }

    @Bean
    @Primary
    public ExpenseClassifier expenseClassifier() {
        return new ExpenseClassifier(null, null, null) {
            @Override
            public ExpenseDto extractExpense(String text) {
                // SIM:EXPENSE;amount=1200;desc=Groceries;source=CREDIT_CARD;date=YYYY-MM-DD
                ExpenseDto dto = new ExpenseDto();
                dto.setValid(true);
                String[] parts = text.split(";");
                for (String p : parts) {
                    if (p.startsWith("amount=")) dto.setAmount(new BigDecimal(p.substring(7)));
                    if (p.startsWith("desc=")) dto.setMerchantName(p.substring(5));
                    if (p.startsWith("source=")) dto.setSourceAccount(p.substring(7));
                    if (p.startsWith("date=")) dto.setTransactionDate(LocalDate.parse(p.substring(5)));
                }
                return dto;
            }

            @Override
            public ExpenseDto extractFieldFromFollowup(ExpenseDto existing, String missingField, String userAnswer) {
                return existing;
            }

            @Override
            public String generateFollowupQuestionForExpense(String field, ExpenseDto dto) {
                return "Please provide " + field;
            }
        };
    }

    @Bean
    @Primary
    public LiabilityPaymentClassifier liabilityPaymentClassifier() {
        return new LiabilityPaymentClassifier(null, null) {
            @Override
            public LiabilityPaymentDto extractPayment(String text) {
                // SIM:PAYMENT;amount=3000;target= CREDIT_CARD;targetName=Card;date=YYYY-MM-DD;source=BANK_ACCOUNT
                LiabilityPaymentDto dto = new LiabilityPaymentDto();
                dto.setValid(true);
                String[] parts = text.split(";");
                for (String p : parts) {
                    if (p.startsWith("amount=")) dto.setAmount(new BigDecimal(p.substring(7)));
                    if (p.startsWith("targetName=")) dto.setTargetName(p.substring(11));
                    if (p.startsWith("date=")) dto.setPaymentDate(LocalDate.parse(p.substring(5)));
                    if (p.startsWith("source=")) dto.setSourceAccount(p.substring(7));
                    if (p.startsWith("target= ")) dto.setTargetLiability("CREDIT_CARD");
                }
                return dto;
            }
        };
    }
}
