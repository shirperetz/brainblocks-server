package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.RacePlayer;

public class LeaderboardEntryResponse {

    private final Long participantId;
    private final String displayName;
    private final String color;
    private final int position;
    private final int score;
    private final int correctAnswers;
    private final int wrongAnswers;
    private final double accuracy;
    private final int currentStreak;
    private final int rank;

    public LeaderboardEntryResponse(Long participantId, String displayName, String color,
                                     int position, int score, int correctAnswers, int wrongAnswers,
                                     double accuracy, int currentStreak, int rank) {
        this.participantId = participantId;
        this.displayName = displayName;
        this.color = color;
        this.position = position;
        this.score = score;
        this.correctAnswers = correctAnswers;
        this.wrongAnswers = wrongAnswers;
        this.accuracy = accuracy;
        this.currentStreak = currentStreak;
        this.rank = rank;
    }

    public static LeaderboardEntryResponse fromPlayer(RacePlayer p, int rank) {
        return new LeaderboardEntryResponse(
                p.getId(), p.getDisplayName(), p.getColor(),
                p.getPosition(), p.getScore(), p.getCorrectAnswers(), p.getWrongAnswers(),
                p.getAccuracy(), p.getCurrentStreak(), rank
        );
    }

    public Long getParticipantId() { return participantId; }
    public String getDisplayName() { return displayName; }
    public String getColor() { return color; }
    public int getPosition() { return position; }
    public int getScore() { return score; }
    public int getCorrectAnswers() { return correctAnswers; }
    public int getWrongAnswers() { return wrongAnswers; }
    public double getAccuracy() { return accuracy; }
    public int getCurrentStreak() { return currentStreak; }
    public int getRank() { return rank; }
}
