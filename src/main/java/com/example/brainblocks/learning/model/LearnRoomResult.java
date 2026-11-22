package com.example.brainblocks.learning.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 2026-11-22 (Shir's room upgrade #3 "השתפרתי"): one child's result in one finished game room, so the
 * next room can say "last time 4 right, today 7". Written once when the room ends.
 */
@Entity
@Table(name = "learn_room_result",
        uniqueConstraints = @UniqueConstraint(name = "uq_room_result", columnNames = {"profileId", "roomKey"}),
        indexes = @Index(name = "ix_room_result_profile", columnList = "profileId"))
public class LearnRoomResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    /** code + start time - one per room. */
    private String roomKey;

    private int answered;

    private int rightCount;

    private int points;

    private int place;

    private LocalDateTime createdAt;

    protected LearnRoomResult() {}

    public LearnRoomResult(Long profileId, String roomKey, int answered, int rightCount, int points, int place) {
        this.profileId = profileId;
        this.roomKey = roomKey;
        this.answered = answered;
        this.rightCount = rightCount;
        this.points = points;
        this.place = place;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getRoomKey() { return roomKey; }
    public int getAnswered() { return answered; }
    public int getRightCount() { return rightCount; }
    public int getPoints() { return points; }
    public int getPlace() { return place; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
