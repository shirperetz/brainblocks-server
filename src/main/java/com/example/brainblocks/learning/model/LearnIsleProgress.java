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
 * 2026-11-13 (Shir: "infinite islands - when an island is done another one comes, with other
 * exercises, and the server keeps everything"): one stage of one game on a generated island
 * ("gen-7"). The base islands keep their progress per topic (learn_stage_progress); a generated
 * island plays the same games again at a higher tier, so its stages are kept here, apart.
 */
@Entity
@Table(name = "learn_isle_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_isle_stage", columnNames = {"profileId", "isleKey", "topic", "stageNumber"}),
        indexes = @Index(name = "ix_isle_profile", columnList = "profileId"))
public class LearnIsleProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Column(nullable = false, length = 20)
    private String isleKey;

    @Column(nullable = false, length = 20)
    private String topic;

    private int stageNumber;
    private int starsEarned;
    private int bestScorePct;
    private int attempts;
    private boolean passed;
    private LocalDateTime updatedAt;

    protected LearnIsleProgress() {}

    public LearnIsleProgress(Long profileId, String isleKey, String topic, int stageNumber) {
        this.profileId = profileId;
        this.isleKey = isleKey;
        this.topic = topic;
        this.stageNumber = stageNumber;
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    public void recordAttempt(int scorePct, int stars, boolean passedNow) {
        attempts++;
        bestScorePct = Math.max(bestScorePct, scorePct);
        starsEarned = Math.max(starsEarned, stars);
        if (passedNow) passed = true;
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getIsleKey() { return isleKey; }
    public String getTopic() { return topic; }
    public int getStageNumber() { return stageNumber; }
    public int getStarsEarned() { return starsEarned; }
    public int getBestScorePct() { return bestScorePct; }
    public int getAttempts() { return attempts; }
    public boolean isPassed() { return passed; }
}
