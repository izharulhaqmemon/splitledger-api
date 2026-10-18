package com.izhar.splitledger.domain;

import jakarta.persistence.*;

/** How much of one expense a single member owes (integer minor units). */
@Entity
@Table(name = "expense_shares")
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_id", nullable = false)
    private Long expenseId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    protected ExpenseShare() {
    }

    public ExpenseShare(Long expenseId, Long memberId, long amountMinor) {
        this.expenseId = expenseId;
        this.memberId = memberId;
        this.amountMinor = amountMinor;
    }

    public Long getId() { return id; }
    public Long getExpenseId() { return expenseId; }
    public Long getMemberId() { return memberId; }
    public long getAmountMinor() { return amountMinor; }
}
