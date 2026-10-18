package com.izhar.splitledger.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable once saved: the ledger is append-only. Money is stored as integer minor units (paisa/cents). */
@Entity
@Table(name = "expenses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "idempotency_key"}))
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 120)
    private String description;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "payer_id", nullable = false)
    private Long payerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "split_type", nullable = false, length = 20)
    private SplitType splitType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    protected Expense() {
    }

    public Expense(Long groupId, String description, long amountMinor, Long payerId,
                   SplitType splitType, String idempotencyKey) {
        this.groupId = groupId;
        this.description = description;
        this.amountMinor = amountMinor;
        this.payerId = payerId;
        this.splitType = splitType;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getDescription() { return description; }
    public long getAmountMinor() { return amountMinor; }
    public Long getPayerId() { return payerId; }
    public SplitType getSplitType() { return splitType; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
