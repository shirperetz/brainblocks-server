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

    // 2026-11-11 (Shir: "a username and password for every child, and it remembers each one").
    // Profiles made before that have none - they stay in the DB, they just can't be logged into.
    /** Lower-case, unique; what the child types to log in. */
    @Column(unique = true, length = 20)
    private String username;

    /** PBKDF2 hash with its salt ("pbkdf2$iterations$salt$hash") - never the password itself. */
    @Column(length = 160)
    private String passwordHash;

    /** Random token the device keeps after login; sent with every request. */
    @Column(unique = true, length = 64)
    private String authToken;

    // 2026-11-11: Shir's admin account (sees every child, resets passwords, blocks, join code).
    // Set only in the database, never by a request. Nullable: old rows read as "no".
    private Boolean admin;

    /** Blocked by the admin: can't log in, the device token stops working. Reversible. */
    private Boolean disabled;

    /**
     * 2026-11-11 (Shir: "nobody gets in without me knowing"): a new sign-up waits for the admin's OK.
     * false = waiting; null = an account from before approvals existed (counts as approved).
     */
    private Boolean approved;

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
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public boolean isAdmin() { return Boolean.TRUE.equals(admin); }
    public void setAdmin(boolean admin) { this.admin = admin; }
    public boolean isDisabled() { return Boolean.TRUE.equals(disabled); }
    public void setDisabled(boolean disabled) { this.disabled = disabled; }
    public boolean isApproved() { return !Boolean.FALSE.equals(approved); }
    public void setApproved(boolean approved) { this.approved = approved; }
}
