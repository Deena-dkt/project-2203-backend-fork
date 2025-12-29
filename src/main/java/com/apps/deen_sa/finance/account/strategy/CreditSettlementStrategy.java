package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.core.value.ValueContainerEntity;

import java.math.BigDecimal;

public interface CreditSettlementStrategy {
    void applyPayment(ValueContainerEntity container, BigDecimal amount);
}
