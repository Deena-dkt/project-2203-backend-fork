package com.apps.deen_sa.finance.account;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.core.value.ValueAdjustmentEntity;
import com.apps.deen_sa.core.value.ValueContainerEntity;
import com.apps.deen_sa.core.value.ValueAdjustmentRepository;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategyResolver;
import com.apps.deen_sa.finance.account.strategy.ValueAdjustmentStrategy;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ValueAdjustmentService {

    private final ValueAdjustmentRepository adjustmentRepository;
    private final ValueAdjustmentStrategyResolver strategyResolver;
    private final ValueContainerService valueContainerService;

    public ValueAdjustmentService(
            ValueAdjustmentRepository adjustmentRepository,
            ValueAdjustmentStrategyResolver strategyResolver,
            ValueContainerService valueContainerService
    ) {
        this.adjustmentRepository = adjustmentRepository;
        this.strategyResolver = strategyResolver;
        this.valueContainerService = valueContainerService;
    }

    @Transactional
    public void apply(ValueContainerEntity container,
                      AdjustmentCommand command) {

        // 1️⃣ Persist audit record
        ValueAdjustmentEntity audit = new ValueAdjustmentEntity();
        audit.setTransactionId(command.getReferenceTxId());
        audit.setContainerId(container.getId());
        audit.setAdjustmentType(command.getType());
        audit.setAmount(command.getAmount());
        audit.setReason(command.getReason());
        audit.setOccurredAt(command.getOccurredAt());
        audit.setCreatedAt(Instant.now());

        adjustmentRepository.save(audit);

        // 2️⃣ Apply strategy
        ValueAdjustmentStrategy strategy =
                strategyResolver.resolve(container);

        strategy.apply(container, command);

        // 3️⃣ Persist container
        container.setLastActivityAt(Instant.now());
        valueContainerService.UpdateValueContainer(container);
    }
}
