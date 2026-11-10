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
@Table(name = "race_results")
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false)
    private RaceRoom raceRoom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private RacePlayer participant;

    @Column(nullable = false)
    private int finalRank;

    @Column(nullable = false)
    private int finalScore;

    @Column(nullable = false)
    private int correctAnswers;

    @Column(nullable = false)
    private int wrongAnswers;

    @Column(nullable = false)
    private double accuracy;

    @Column(nullable = false)
    private double averageAnswerTimeMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RaceResult() {}

    public RaceResult(RaceRoom raceRoom, RacePlayer participant, int finalRank) {
        this.raceRoom = raceRoom;
        this.participant = participant;
        this.finalRank = finalRank;
        this.finalScore = participant.getScore();
        this.correctAnswers = participant.getCorrectAnswers();
        this.wrongAnswers = participant.getWrongAnswers();
        this.accuracy = participant.getAccuracy();
        this.averageAnswerTimeMs = participant.getAverageAnswerTimeMs();
    }

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public RaceRoom getRaceRoom() { return raceRoom; }
    public RacePlayer getParticipant() { return participant; }
    public int getFinalRank() { return finalRank; }
    public int getFinalScore() { return finalScore; }
    public int getCorrectAnswers() { return correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public double getAccuracy() { return accuracy; }
    public double getAverageAnswerTimeMs() { return averageAnswerTimeMs; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
