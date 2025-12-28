package com.apps.deen_sa.strategy;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.entity.ValueContainerEntity;

public interface ValueAdjustmentStrategy {

    boolean supports(ValueContainerEntity container);

    void apply(ValueContainerEntity container, AdjustmentCommand command);

    void reverse(ValueContainerEntity container, AdjustmentCommand command);
}
