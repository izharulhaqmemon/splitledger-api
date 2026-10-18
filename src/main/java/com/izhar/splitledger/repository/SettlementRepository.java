package com.izhar.splitledger.repository;

import com.izhar.splitledger.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByGroupIdOrderByCreatedAtAsc(Long groupId);

    Optional<Settlement> findByGroupIdAndIdempotencyKey(Long groupId, String idempotencyKey);
}
