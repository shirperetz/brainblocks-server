package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 2026-11-27 (Shir: idea #10): a prize from the teacher - a badge emoji, coins for the island, a personal sentence. */
@Entity
@Table(name = "learn_award")
public class LearnAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;
    private Long fromId;
    @Column(length = 60)
    private String fromName;
    private Long classId; // set when the whole class got it
    @Column(length = 8)
    private String emoji;
    private int coins;
    @Column(length = 140)
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime seenAt;

    public LearnAward() {}

    public LearnAward(Long profileId, Long fromId, String fromName, Long classId, String emoji, int coins, String message, LocalDateTime now) {
        this.profileId = profileId;
        this.fromId = fromId;
        this.fromName = fromName;
        this.classId = classId;
        this.emoji = emoji;
        this.coins = coins;
        this.message = message;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public Long getFromId() { return fromId; }
    public String getFromName() { return fromName; }
    public Long getClassId() { return classId; }
    public String getEmoji() { return emoji; }
    public int getCoins() { return coins; }
    public String getMessage() { return message; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSeenAt() { return seenAt; }
    public void setSeenAt(LocalDateTime t) { this.seenAt = t; }
}
