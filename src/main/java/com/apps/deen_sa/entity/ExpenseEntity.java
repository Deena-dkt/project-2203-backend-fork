package com.apps.deen_sa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Entity
@Table(name = "expense")
@Getter
@Setter
public class ExpenseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 50)
    private String category;

    @Column(length = 100)
    private String subcategory;

    @Column(length = 200)
    private String merchant;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "spent_at", nullable = false)
    private OffsetDateTime spentAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "raw_text")
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
}

