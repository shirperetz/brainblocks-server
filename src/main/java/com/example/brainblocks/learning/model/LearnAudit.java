package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 2026-11-13: one change made by the admin or a teacher (who, what - method + path - and when). */
@Entity
@Table(name = "learn_audit", indexes = @Index(name = "ix_audit_at", columnList = "at"))
public class LearnAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;

    @Column(length = 8)
    private String method;

    @Column(length = 160)
    private String path;

    private LocalDateTime at;

    protected LearnAudit() {}

    public LearnAudit(Long actorId, String method, String path) {
        this.actorId = actorId;
        this.method = method;
        this.path = path.length() > 160 ? path.substring(0, 160) : path;
        this.at = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public LocalDateTime getAt() { return at; }
}
