package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 2026-11-13 (Shir: "buy, build and upgrade on my island, with the stars"): one thing a child owns on
 * the 3D island. kind = a building ("house", level 1-3) on a slot, "land" (a bought piece of land,
 * slot = 100 + n) or "chest:KEY" (a treasure chest opened for finishing that island, slot = -1).
 * Coins are never stored: earned (every star) minus spent (the catalog prices of what is here).
 */
@Entity
@Table(name = "learn_island_item",
        uniqueConstraints = @UniqueConstraint(name = "uq_island_item", columnNames = {"profileId", "kind", "slot"}),
        indexes = @Index(name = "ix_island_item_profile", columnList = "profileId"))
public class LearnIslandItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Column(nullable = false, length = 40)
    private String kind;

    private int slot;
    private int level;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected LearnIslandItem() {}

    public LearnIslandItem(Long profileId, String kind, int slot, int level) {
        this.profileId = profileId;
        this.kind = kind;
        this.slot = slot;
        this.level = level;
    }

    @PrePersist
    void created() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    public void levelUp() {
        level++;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getKind() { return kind; }
    public int getSlot() { return slot; }
    public int getLevel() { return level; }
}
