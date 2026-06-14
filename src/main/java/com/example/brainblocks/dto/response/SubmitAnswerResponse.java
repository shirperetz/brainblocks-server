package com.example.brainblocks.dto.response;

public class SubmitAnswerResponse {

    private final Long questionId;
    private final Long playerId;
    private final int submittedAnswer;
    private final boolean correct;

    public SubmitAnswerResponse(Long questionId, Long playerId, int submittedAnswer, boolean correct) {
        this.questionId = questionId;
        this.playerId = playerId;
        this.submittedAnswer = submittedAnswer;
        this.correct = correct;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public int getSubmittedAnswer() {
        return submittedAnswer;
    }

    public boolean isCorrect() {
        return correct;
    }
}
