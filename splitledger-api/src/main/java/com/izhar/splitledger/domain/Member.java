package com.izhar.splitledger.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = 60)
    private String name;

    protected Member() {
    }

    public Member(Long groupId, String name) {
        this.groupId = groupId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getGroupId() { return groupId; }
    public String getName() { return name; }
}
