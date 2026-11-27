package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-26 (Shir: idea #9 - a report for parents): a read-only link the teacher sends home.
 * Only the SHA-256 of the link's code is kept; one active link per child; it ends after 30 days.
 */
@Entity
@Table(name = "learn_parent_link")
public class LearnParentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    @Column(length = 64, nullable = false, unique = true)
    private String codeHash;

    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastViewedAt;

    public LearnParentLink() {}

    public LearnParentLink(Long profileId, String codeHash, Long createdBy, LocalDateTime now, LocalDateTime expiresAt) {
        this.profileId = profileId;
        this.codeHash = codeHash;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getCodeHash() { return codeHash; }
    public Long getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }
    public void setLastViewedAt(LocalDateTime t) { this.lastViewedAt = t; }
}
