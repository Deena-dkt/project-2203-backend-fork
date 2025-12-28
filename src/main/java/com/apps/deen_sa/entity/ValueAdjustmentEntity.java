package com.apps.deen_sa.entity;

import com.apps.deen_sa.utils.AdjustmentTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "value_adjustments")
public class ValueAdjustmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long transactionId;

    private Long containerId;

    @Enumerated(EnumType.STRING)
    private AdjustmentTypeEnum adjustmentType; // DEBIT / CREDIT

    private BigDecimal amount;

    private String reason; // EXPENSE, REVERSAL, EDIT

    private Instant occurredAt;

    private Instant createdAt;
}
