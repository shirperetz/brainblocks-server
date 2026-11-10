package com.example.brainblocks.dto.response;

import java.util.List;

public class RaceResultsResponse {

    private final String roomCode;
    private final String roomName;
    private final List<ResultEntryResponse> results;

    public RaceResultsResponse(String roomCode, String roomName, List<ResultEntryResponse> results) {
        this.roomCode = roomCode;
        this.roomName = roomName;
        this.results = results;
    }

    public String getRoomCode() { return roomCode; }
    public String getRoomName() { return roomName; }
    public List<ResultEntryResponse> getResults() { return results; }

    public static class ResultEntryResponse {
        private final Long participantId;
        private final String displayName;
        private final String color;
        private final int rank;
        private final int finalScore;
        private final int correctAnswers;
        private final int wrongAnswers;
        private final double accuracy;
        private final double averageAnswerTimeMs;
        private final int bestStreak;

        public ResultEntryResponse(Long participantId, String displayName, String color, int rank,
                                    int finalScore, int correctAnswers, int wrongAnswers,
                                    double accuracy, double averageAnswerTimeMs, int bestStreak) {
            this.participantId = participantId;
            this.displayName = displayName;
            this.color = color;
            this.rank = rank;
            this.finalScore = finalScore;
            this.correctAnswers = correctAnswers;
            this.wrongAnswers = wrongAnswers;
            this.accuracy = accuracy;
            this.averageAnswerTimeMs = averageAnswerTimeMs;
            this.bestStreak = bestStreak;
        }

        public Long getParticipantId() { return participantId; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
        public int getRank() { return rank; }
        public int getFinalScore() { return finalScore; }
        public int getCorrectAnswers() { return correctAnswers; }
        public int getWrongAnswers() { return wrongAnswers; }
        public double getAccuracy() { return accuracy; }
        public double getAverageAnswerTimeMs() { return averageAnswerTimeMs; }
        public int getBestStreak() { return bestStreak; }
    }
}
