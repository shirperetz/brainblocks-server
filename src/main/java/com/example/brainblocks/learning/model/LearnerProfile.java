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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single child's learning profile: the avatar they picked, their grade/difficulty,
 * accumulated points and daily-challenge streak. All progress for the kids learning
 * game ("עולם החשבון") hangs off this entity.
 */
@Entity
@Table(name = "learn_profile")
public class LearnerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 24)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Avatar avatar;

    @Enumerated(EnumType.STRING)
    @Column(length = 8)
    private Grade grade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Difficulty difficulty;

    @Column(nullable = false)
    private int totalPoints;

    @Column(nullable = false)
    private int dailyStreak;

    @Column
    private LocalDate lastDailyDate;

    @Column(nullable = false)
    private boolean soundEnabled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastActiveAt;

    protected LearnerProfile() {}

    public LearnerProfile(String name, Avatar avatar) {
        this.name = name;
        this.avatar = avatar;
        this.difficulty = Difficulty.MEDIUM;
        this.totalPoints = 0;
        this.dailyStreak = 0;
        this.soundEnabled = true;
    }

    public void addPoints(int delta) {
        this.totalPoints = Math.max(0, this.totalPoints + delta);
    }

    /**
     * The daily challenge was finished on {@code today}: yesterday too -> streak grows; already
     * today -> unchanged; a gap -> starts again at 1. Returns true when it counted (first today).
     */
    public boolean completeDaily(LocalDate today) {
        if (today.equals(lastDailyDate)) return false;
        dailyStreak = today.minusDays(1).equals(lastDailyDate) ? dailyStreak + 1 : 1;
        lastDailyDate = today;
        return true;
    }

    public void touch() {
        this.lastActiveAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Avatar getAvatar() { return avatar; }
    public void setAvatar(Avatar avatar) { this.avatar = avatar; }
    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty difficulty) { this.difficulty = difficulty; }
    public int getTotalPoints() { return totalPoints; }
    public void setTotalPoints(int totalPoints) { this.totalPoints = totalPoints; }
    public int getDailyStreak() { return dailyStreak; }
    public void setDailyStreak(int dailyStreak) { this.dailyStreak = dailyStreak; }
    public LocalDate getLastDailyDate() { return lastDailyDate; }
    public void setLastDailyDate(LocalDate lastDailyDate) { this.lastDailyDate = lastDailyDate; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastActiveAt() { return lastActiveAt; }
}
