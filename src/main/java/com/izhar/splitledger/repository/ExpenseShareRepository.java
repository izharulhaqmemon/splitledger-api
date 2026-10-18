package com.izhar.splitledger.repository;

import com.izhar.splitledger.domain.ExpenseShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ExpenseShareRepository extends JpaRepository<ExpenseShare, Long> {
    List<ExpenseShare> findByExpenseIdIn(Collection<Long> expenseIds);
}
