package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-14 (Shir: "give me the code in case I change phones"): a one-time backup code for the admin's
 * second step. Only its SHA-256 is kept; each code works once.
 */
@Entity
@Table(name = "learn_recovery_code")
public class LearnRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    @Column(length = 64)
    private String codeHash;

    private LocalDateTime createdAt;
    private LocalDateTime usedAt;

    protected LearnRecoveryCode() {
    }

    public LearnRecoveryCode(Long profileId, String codeHash) {
        this.profileId = profileId;
        this.codeHash = codeHash;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getCodeHash() { return codeHash; }
    public LocalDateTime getUsedAt() { return usedAt; }
    public void setUsedAt(LocalDateTime usedAt) { this.usedAt = usedAt; }
}
