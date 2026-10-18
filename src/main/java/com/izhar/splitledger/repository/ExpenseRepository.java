package com.izhar.splitledger.repository;

import com.izhar.splitledger.domain.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByGroupId(Long groupId);

    Page<Expense> findAllByGroupId(Long groupId, Pageable pageable);

    Page<Expense> findAllByGroupIdAndPayerId(Long groupId, Long payerId, Pageable pageable);

    Optional<Expense> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);
}
