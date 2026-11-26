package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-26 (security review #14): a logged-in device. Only the SHA-256 of its token is kept - someone
 * who reads the database can't log in as a child. One row per device (log out = this device only).
 */
@Entity
@Table(name = "learn_token", indexes = @Index(name = "ix_learn_token_hash", columnList = "tokenHash", unique = true))
public class LearnToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    @Column(length = 64, nullable = false)
    private String tokenHash;

    private LocalDateTime createdAt;
    private LocalDateTime usedAt;

    public LearnToken() {}

    public LearnToken(Long profileId, String tokenHash, LocalDateTime now) {
        this.profileId = profileId;
        this.tokenHash = tokenHash;
        this.createdAt = now;
        this.usedAt = now;
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
