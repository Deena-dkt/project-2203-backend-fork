package com.apps.deen_sa.simulation;

import com.apps.deen_sa.dto.AccountSetupDto;
import com.apps.deen_sa.dto.AssetDto;
import com.apps.deen_sa.dto.ExpenseDto;
import com.apps.deen_sa.dto.LiabilityPaymentDto;
import com.apps.deen_sa.llm.impl.AccountSetupClassifier;
import com.apps.deen_sa.llm.impl.AssetClassifier;
import com.apps.deen_sa.llm.impl.ExpenseClassifier;
import com.apps.deen_sa.llm.impl.LiabilityPaymentClassifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.*;

@TestConfiguration
public class LLMTestConfiguration {

    @Bean
    @Primary
    public AccountSetupClassifier accountSetupClassifier() {
        AccountSetupClassifier mock = mock(AccountSetupClassifier.class);
        
        when(mock.extractAccount(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // Expect SIM:ACCOUNT;type=...;name=...;current=...;date=YYYY-MM-DD
            // Extended to support: limit=...;dueDay=...;currency=...
            AccountSetupDto dto = new AccountSetupDto();
            dto.setValid(true);
            dto.setCurrency("INR"); // default

            String[] parts = text.split(";");
            java.util.Map<String, Object> details = new java.util.HashMap<>();
            
            for (String p : parts) {
                if (p.startsWith("type=")) dto.setContainerType(p.substring(5));
                if (p.startsWith("name=")) dto.setName(p.substring(5));
                if (p.startsWith("current=")) dto.setCurrentValue(new BigDecimal(p.substring(8)));
                if (p.startsWith("limit=")) dto.setCapacityLimit(new BigDecimal(p.substring(6)));
                if (p.startsWith("dueDay=")) details.put("dueDay", Integer.parseInt(p.substring(7)));
                if (p.startsWith("currency=")) dto.setCurrency(p.substring(9));
            }
            
            if (!details.isEmpty()) {
                dto.setDetails(details);
            }
            
            return dto;
        });
        
        return mock;
    }

    @Bean
    @Primary
    public ExpenseClassifier expenseClassifier() {
        ExpenseClassifier mock = mock(ExpenseClassifier.class);
        
        when(mock.extractExpense(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // SIM:EXPENSE;amount=1200;desc=Groceries;source=CREDIT_CARD;category=Shopping;date=YYYY-MM-DD
            ExpenseDto dto = new ExpenseDto();
            dto.setValid(true);
            dto.setCategory("GENERAL"); // Set default category
            String[] parts = text.split(";");
            for (String p : parts) {
                if (p.startsWith("amount=")) dto.setAmount(new BigDecimal(p.substring(7)));
                if (p.startsWith("desc=")) dto.setMerchantName(p.substring(5));
                if (p.startsWith("source=")) dto.setSourceAccount(p.substring(7));
                if (p.startsWith("category=")) dto.setCategory(p.substring(9));
                if (p.startsWith("date=")) dto.setTransactionDate(LocalDate.parse(p.substring(5)));
                if (p.startsWith("category=")) dto.setCategory(p.substring(9));
            }
            return dto;
        });

        when(mock.extractFieldFromFollowup(any(), anyString(), anyString())).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        when(mock.generateFollowupQuestionForExpense(anyString(), any())).thenAnswer(invocation ->
            "Please provide " + invocation.getArgument(0)
        );
        
        return mock;
    }

    @Bean
    @Primary
    public LiabilityPaymentClassifier liabilityPaymentClassifier() {
        LiabilityPaymentClassifier mock = mock(LiabilityPaymentClassifier.class);
        
        when(mock.extractPayment(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            // SIM:PAYMENT;amount=3000;target=CREDIT_CARD;targetName=Card;date=YYYY-MM-DD;source=BANK_ACCOUNT
            LiabilityPaymentDto dto = new LiabilityPaymentDto();
            dto.setValid(true);
            String[] parts = text.split(";");
            for (String p : parts) {
                if (p.startsWith("amount=")) dto.setAmount(new BigDecimal(p.substring(7)));
                if (p.startsWith("targetName=")) dto.setTargetName(p.substring(11));
                if (p.startsWith("date=")) dto.setPaymentDate(LocalDate.parse(p.substring(5)));
                if (p.startsWith("source=")) dto.setSourceAccount(p.substring(7));
                if (p.startsWith("target=")) dto.setTargetLiability(p.substring(7).trim());
            }
            return dto;
        });
        
        return mock;
    }

    @Bean
    @Primary
    public AssetClassifier assetClassifier() {
        AssetClassifier mock = mock(AssetClassifier.class);
        
        when(mock.extractAsset(anyString())).thenAnswer(invocation -> {
            String text = invocation.getArgument(0);
            AssetDto dto = new AssetDto();
            
            // Simple parsing for common asset declarations
            String lowerText = text.toLowerCase();
            
            if (lowerText.contains("shares") || lowerText.contains("units") || lowerText.contains("grams") || lowerText.contains("gold")) {
                dto.setValid(true);
                
                // Extract quantity (look for numbers)
                String[] words = text.split("\\s+");
                BigDecimal quantity = null;
                int quantityIndex = -1;
                
                for (int i = 0; i < words.length; i++) {
                    try {
                        quantity = new BigDecimal(words[i]);
                        quantityIndex = i;
                        dto.setQuantity(quantity);
                        break;
                    } catch (NumberFormatException e) {
                        // Continue searching
                    }
                }
                
                // Extract asset identifier
                if (quantityIndex >= 0 && quantityIndex < words.length - 1) {
                    String nextWord = words[quantityIndex + 1];
                    String lowerNextWord = nextWord.toLowerCase();
                    
                    // Handle different patterns:
                    // "100 ITC shares" -> ITC is identifier
                    // "50 units of SBI Bluechip" -> SBI Bluechip is identifier
                    // "20 grams of gold" -> gold is identifier
                    
                    if (lowerNextWord.equals("units") || lowerNextWord.equals("grams") || lowerNextWord.equals("shares")) {
                        // Unit comes right after number, look for identifier after unit
                        // Could be "100 shares" (no identifier yet) or "100 shares ITC" (rare)
                        // Most common: identifier comes BEFORE quantity like "ITC shares"
                        // But our test cases have identifier AFTER quantity
                        // Actually, let's look for pattern: number + identifier + unit
                        // OR: number + unit + "of" + identifier
                        if (quantityIndex < words.length - 2) {
                            String afterUnit = words[quantityIndex + 2];
                            if (afterUnit.equalsIgnoreCase("of") && quantityIndex < words.length - 3) {
                                // "50 units of SBI Bluechip mutual fund"
                                StringBuilder identifier = new StringBuilder();
                                for (int i = quantityIndex + 3; i < words.length; i++) {
                                    String word = words[i];
                                    if (word.equalsIgnoreCase("mutual") || word.equalsIgnoreCase("fund")) {
                                        break;
                                    }
                                    if (identifier.length() > 0) identifier.append(" ");
                                    identifier.append(word);
                                }
                                if (identifier.length() > 0) {
                                    dto.setAssetIdentifier(identifier.toString());
                                }
                            }
                        }
                    } else if (lowerNextWord.equals("of")) {
                        // "20 of gold" pattern - identifier after "of"
                        if (quantityIndex < words.length - 2) {
                            dto.setAssetIdentifier(words[quantityIndex + 2]);
                        }
                    } else {
                        // "100 ITC shares" - identifier is right after number
                        dto.setAssetIdentifier(nextWord);
                    }
                }
                
                // Extract unit
                if (lowerText.contains("shares")) dto.setUnit("shares");
                else if (lowerText.contains("units")) dto.setUnit("units");
                else if (lowerText.contains("grams")) dto.setUnit("grams");
                
            } else {
                dto.setValid(false);
                dto.setReason("Could not extract asset details");
            }
            
            return dto;
        });
        
        when(mock.generateFollowupQuestion(anyString())).thenAnswer(invocation -> {
            String field = invocation.getArgument(0);
            return switch (field) {
                case "broker" -> "Which broker or platform is this asset held in?";
                case "investedAmount" -> "Do you remember roughly how much you invested in this?";
                default -> "Could you provide more details about " + field + "?";
            };
        });
        
        return mock;
    }
}
