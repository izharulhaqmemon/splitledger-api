package com.izhar.splitledger.repository;

import com.izhar.splitledger.domain.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<ExpenseGroup, Long> {
}
