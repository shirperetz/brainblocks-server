package com.example.brainblocks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "game_events")
public class GameEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false)
    private RaceRoom raceRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private RacePlayer participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GameEventType type;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(nullable = false)
    private int pointsDelta;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected GameEvent() {}

    public GameEvent(RaceRoom raceRoom, RacePlayer participant, GameEventType type, String message, int pointsDelta) {
        this.raceRoom = raceRoom;
        this.participant = participant;
        this.type = type;
        this.message = message;
        this.pointsDelta = pointsDelta;
    }

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public RaceRoom getRaceRoom() { return raceRoom; }
    public RacePlayer getParticipant() { return participant; }
    public GameEventType getType() { return type; }
    public String getMessage() { return message; }
    public int getPointsDelta() { return pointsDelta; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
