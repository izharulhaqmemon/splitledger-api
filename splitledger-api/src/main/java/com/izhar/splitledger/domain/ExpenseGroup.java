package com.izhar.splitledger.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "expense_groups")
public class ExpenseGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ExpenseGroup() {
    }

    public ExpenseGroup(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
