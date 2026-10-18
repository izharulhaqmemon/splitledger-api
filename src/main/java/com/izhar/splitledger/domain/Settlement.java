package com.izhar.splitledger.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** A real-world payment from one member to another that reduces what is owed. */
@Entity
@Table(name = "settlements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "idempotency_key"}))
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "from_member_id", nullable = false)
    private Long fromMemberId;

    @Column(name = "to_member_id", nullable = false)
    private Long toMemberId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    protected Settlement() {
    }

    public Settlement(Long groupId, Long fromMemberId, Long toMemberId, long amountMinor, String idempotencyKey) {
        this.groupId = groupId;
        this.fromMemberId = fromMemberId;
        this.toMemberId = toMemberId;
        this.amountMinor = amountMinor;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public Long getFromMemberId() { return fromMemberId; }
    public Long getToMemberId() { return toMemberId; }
    public long getAmountMinor() { return amountMinor; }
    public Instant getCreatedAt() { return createdAt; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
