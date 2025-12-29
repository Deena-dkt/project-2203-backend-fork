package com.apps.deen_sa.strategy;

import com.apps.deen_sa.entity.ValueContainerEntity;

import java.math.BigDecimal;

public interface CreditSettlementStrategy {
    void applyPayment(ValueContainerEntity container, BigDecimal amount);
}
