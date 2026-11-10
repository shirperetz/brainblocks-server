package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.PlayerStatus;
import com.example.brainblocks.model.RacePlayer;

import java.time.LocalDateTime;

public class RacePlayerResponse {

    private final Long id;
    private final String displayName;
    private final PlayerStatus status;
    private final String color;
    private final int position;
    private final int score;
    private final int correctAnswers;
    private final int wrongAnswers;
    private final double accuracy;
    private final double averageAnswerTimeMs;
    private final int currentStreak;
    private final int bestStreak;
    private final boolean pendingDecision;
    private final LocalDateTime joinedAt;
    private final LocalDateTime finishedAt;

    public RacePlayerResponse(Long id, String displayName, PlayerStatus status, String color,
                               int position, int score, int correctAnswers, int wrongAnswers,
                               double accuracy, double averageAnswerTimeMs, int currentStreak,
                               int bestStreak, boolean pendingDecision,
                               LocalDateTime joinedAt, LocalDateTime finishedAt) {
        this.id = id;
        this.displayName = displayName;
        this.status = status;
        this.color = color;
        this.position = position;
        this.score = score;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.accuracy = accuracy;
        this.averageAnswerTimeMs = averageAnswerTimeMs;
        this.currentStreak = currentStreak;
        this.bestStreak = bestStreak;
        this.pendingDecision = pendingDecision;
        this.joinedAt = joinedAt;
        this.finishedAt = finishedAt;
    }

    public static RacePlayerResponse fromEntity(RacePlayer p) {
        return new RacePlayerResponse(
                p.getId(), p.getDisplayName(), p.getStatus(), p.getColor(),
                p.getPosition(), p.getScore(), p.getCorrectAnswers(), p.getWrongAnswers(),
                p.getAccuracy(), p.getAverageAnswerTimeMs(), p.getCurrentStreak(),
                p.getBestStreak(), p.isPendingDecision(), p.getJoinedAt(), p.getFinishedAt()
        );
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public PlayerStatus getStatus() { return status; }
    public String getColor() { return color; }
    public int getPosition() { return position; }
    public int getScore() { return score; }
    public int getCorrectAnswers() { return correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public double getAccuracy() { return accuracy; }
    public double getAverageAnswerTimeMs() { return averageAnswerTimeMs; }
    public int getCurrentStreak() { return currentStreak; }
    public int getBestStreak() { return bestStreak; }
    public boolean isPendingDecision() { return pendingDecision; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
