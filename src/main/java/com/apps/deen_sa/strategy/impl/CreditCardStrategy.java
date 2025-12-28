package com.apps.deen_sa.strategy.impl;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.entity.ValueContainerEntity;
import com.apps.deen_sa.strategy.ValueAdjustmentStrategy;
import org.springframework.stereotype.Component;

@Component
public class CreditCardStrategy implements ValueAdjustmentStrategy {

    @Override
    public boolean supports(ValueContainerEntity container) {
        return container.getContainerType().equals("CREDIT");
    }

    @Override
    public void apply(ValueContainerEntity container, AdjustmentCommand cmd) {
        container.setAvailableValue(
                container.getAvailableValue().subtract(cmd.getAmount())
        );
    }

    @Override
    public void reverse(ValueContainerEntity container, AdjustmentCommand cmd) {
        container.setAvailableValue(
                container.getAvailableValue().add(cmd.getAmount())
        );
    }
}
