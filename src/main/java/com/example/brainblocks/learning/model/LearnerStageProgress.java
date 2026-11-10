package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One child's progress on one stage of one topic. Rows are created lazily:
 * a stage only gets a row once it becomes unlocked or is played. Absence of a row
 * means "locked" (except stage 1, which is always open).
 */
@Entity
@Table(
    name = "learn_stage_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"profileId", "topic", "stageNumber"})
)
public class LearnerStageProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    // varchar, not a MySQL ENUM: a new Topic value must not need an ALTER (2026-11-10)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Topic topic;

    @Column(nullable = false)
    private int stageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private StageStatus status;

    @Column(nullable = false)
    private int starsEarned;

    @Column(nullable = false)
    private int bestScorePct;

    @Column(nullable = false)
    private int attempts;

    protected LearnerStageProgress() {}

    public LearnerStageProgress(Long profileId, Topic topic, int stageNumber, StageStatus status) {
        this.profileId = profileId;
        this.topic = topic;
        this.stageNumber = stageNumber;
        this.status = status;
        this.starsEarned = 0;
        this.bestScorePct = 0;
        this.attempts = 0;
    }

    /** Records a finished attempt, keeping the best stars/score and marking the stage complete if passed. */
    public void recordAttempt(int scorePct, int stars, boolean passed) {
        this.attempts++;
        this.bestScorePct = Math.max(this.bestScorePct, scorePct);
        this.starsEarned = Math.max(this.starsEarned, stars);
        if (passed) {
            this.status = StageStatus.COMPLETED;
        }
    }

    public void unlock() {
        if (this.status == StageStatus.LOCKED) {
            this.status = StageStatus.UNLOCKED;
        }
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public Topic getTopic() { return topic; }
    public int getStageNumber() { return stageNumber; }
    public StageStatus getStatus() { return status; }
    public int getStarsEarned() { return starsEarned; }
    public int getBestScorePct() { return bestScorePct; }
    public int getAttempts() { return attempts; }
}
