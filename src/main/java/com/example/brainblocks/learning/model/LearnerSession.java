package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A single finished practice run (a stage, the daily challenge, or a timed game).
 * Feeds the end-of-stage summary and the parent dashboard.
 */
@Entity
@Table(name = "learn_session")
public class LearnerSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    // varchar, not a MySQL ENUM: a new Topic value must not need an ALTER (2026-11-10)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Grade grade;

    @Column(nullable = false)
    private int stageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SessionMode mode;

    @Column(nullable = false)
    private int totalQuestions;

    @Column(nullable = false)
    private int correctCount;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private int stars;

    @Column(nullable = false)
    private int hintsUsed;

    @Column(nullable = false)
    private long durationMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearnerSession() {}

    public LearnerSession(Long profileId, Topic topic, Grade grade, int stageNumber, SessionMode mode,
                          int totalQuestions, int correctCount, int points, int stars,
                          int hintsUsed, long durationMs) {
        this.profileId = profileId;
        this.topic = topic;
        this.grade = grade;
        this.stageNumber = stageNumber;
        this.mode = mode;
        this.totalQuestions = totalQuestions;
        this.correctCount = correctCount;
        this.points = points;
        this.stars = stars;
        this.hintsUsed = hintsUsed;
        this.durationMs = durationMs;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public Topic getTopic() { return topic; }
    public Grade getGrade() { return grade; }
    public int getStageNumber() { return stageNumber; }
    public SessionMode getMode() { return mode; }
    public int getTotalQuestions() { return totalQuestions; }
    public int getCorrectCount() { return correctCount; }
    public int getPoints() { return points; }
    public int getStars() { return stars; }
    public int getHintsUsed() { return hintsUsed; }
    public long getDurationMs() { return durationMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
