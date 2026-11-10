package com.example.brainblocks.dto.response;

public class AnswerResponse {

    private final Long questionId;
    private final Long participantId;
    private final int submittedAnswer;
    private final boolean correct;
    private final int pointsEarned;
    private final int newScore;
    private final int newPosition;
    private final int currentStreak;
    private final boolean pendingDecision;
    private final String luckEvent;
    private final String luckMessage;
    private final int luckPointsDelta;

    public AnswerResponse(Long questionId, Long participantId, int submittedAnswer, boolean correct,
                           int pointsEarned, int newScore, int newPosition, int currentStreak,
                           boolean pendingDecision, String luckEvent, String luckMessage, int luckPointsDelta) {
        this.questionId = questionId;
        this.participantId = participantId;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
        this.pointsEarned = pointsEarned;
        this.newScore = newScore;
        this.newPosition = newPosition;
        this.currentStreak = currentStreak;
        this.pendingDecision = pendingDecision;
        this.luckEvent = luckEvent;
        this.luckMessage = luckMessage;
        this.luckPointsDelta = luckPointsDelta;
    }

    public Long getQuestionId() { return questionId; }
    public Long getParticipantId() { return participantId; }
    public int getSubmittedAnswer() { return submittedAnswer; }
    public boolean isCorrect() { return correct; }
    public int getPointsEarned() { return pointsEarned; }
    public int getNewScore() { return newScore; }
    public int getNewPosition() { return newPosition; }
    public int getCurrentStreak() { return currentStreak; }
    public boolean isPendingDecision() { return pendingDecision; }
    public String getLuckEvent() { return luckEvent; }
    public String getLuckMessage() { return luckMessage; }
    public int getLuckPointsDelta() { return luckPointsDelta; }
}
