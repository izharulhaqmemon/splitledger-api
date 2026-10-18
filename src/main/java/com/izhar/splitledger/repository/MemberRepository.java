package com.izhar.splitledger.repository;

import com.izhar.splitledger.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByGroupIdOrderByIdAsc(Long groupId);
}
