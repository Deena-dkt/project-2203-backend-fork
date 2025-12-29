package com.apps.deen_sa.finance.account.strategy;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.core.value.ValueContainerEntity;

public interface ValueAdjustmentStrategy {

    boolean supports(ValueContainerEntity container);

    void apply(ValueContainerEntity container, AdjustmentCommand command);

    void reverse(ValueContainerEntity container, AdjustmentCommand command);
}
