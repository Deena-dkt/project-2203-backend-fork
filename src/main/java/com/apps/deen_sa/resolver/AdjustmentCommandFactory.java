package com.apps.deen_sa.resolver;

import com.apps.deen_sa.dto.AdjustmentCommand;
import com.apps.deen_sa.entity.TransactionEntity;
import com.apps.deen_sa.utils.AdjustmentTypeEnum;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class AdjustmentCommandFactory {

    public AdjustmentCommand forExpense(TransactionEntity tx) {

        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.DEBIT,
                "EXPENSE",
                tx.getId(),
                Instant.now()
        );
    }

    public AdjustmentCommand forExpenseReversal(TransactionEntity tx) {

        return new AdjustmentCommand(
                tx.getAmount(),
                AdjustmentTypeEnum.CREDIT,
                "EXPENSE_REVERSAL",
                tx.getId(),
                Instant.now()
        );
    }
}
