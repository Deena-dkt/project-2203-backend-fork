package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.core.value.ValueContainerEntity;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategy;
import org.springframework.stereotype.Component;

@Component
public class CashLikeStrategy implements ValueAdjustmentStrategy {

    @Override
    public boolean supports(ValueContainerEntity container) {
        return container.getContainerType().equals("CASH")
                || container.getContainerType().equals("BANK_ACCOUNT")
                || container.getContainerType().equals("WALLET");
    }

    @Override
    public void apply(ValueContainerEntity container, AdjustmentCommand cmd) {
        container.setCurrentValue(
                container.getCurrentValue().subtract(cmd.getAmount())
        );
        container.setAvailableValue(container.getCurrentValue());
    }

    @Override
    public void reverse(ValueContainerEntity container, AdjustmentCommand cmd) {
        container.setCurrentValue(
                container.getCurrentValue().add(cmd.getAmount())
        );
        container.setAvailableValue(container.getCurrentValue());
    }
}
