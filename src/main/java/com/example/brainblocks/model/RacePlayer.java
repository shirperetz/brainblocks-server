package com.example.brainblocks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "race_players")
public class RacePlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false)
    private RaceRoom raceRoom;

    @Column(nullable = false, length = 24)
    private String displayName;

    @Column(nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    protected RacePlayer() {
    }

    public RacePlayer(RaceRoom raceRoom, String displayName) {
        this.raceRoom = raceRoom;
        this.displayName = displayName;
    }

    @PrePersist
    void onCreate() {
        joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public RaceRoom getRaceRoom() {
        return raceRoom;
    }

    public String getDisplayName() {
        return displayName;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
