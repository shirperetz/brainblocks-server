package com.example.brainblocks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "race_rooms")
public class RaceRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 8)
    private String roomCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 64)
    private String teacherKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RaceRoomStatus status;

    @Column(nullable = false)
    private int maxPlayers;

    @Column(nullable = false)
    private int minPlayers;

    @Column(nullable = false)
    private int trackLength;

    @Column(nullable = false)
    private boolean requiresApproval;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime finishedAt;

    protected RaceRoom() {}

    public RaceRoom(String roomCode, String name, String teacherKey, RaceRoomStatus status,
                    int maxPlayers, int minPlayers, int trackLength, boolean requiresApproval) {
        this.roomCode = roomCode;
        this.name = name;
        this.teacherKey = teacherKey;
        this.status = status;
        this.maxPlayers = maxPlayers;
        this.minPlayers = minPlayers;
        this.trackLength = trackLength;
        this.requiresApproval = requiresApproval;
    }

    public void startRace() {
        this.status = RaceRoomStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void finishRace() {
        this.status = RaceRoomStatus.FINISHED;
        this.finishedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getName() { return name; }
    public String getTeacherKey() { return teacherKey; }
    public RaceRoomStatus getStatus() { return status; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getMinPlayers() { return minPlayers; }
    public int getTrackLength() { return trackLength; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
