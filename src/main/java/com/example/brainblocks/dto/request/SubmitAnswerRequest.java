package com.example.brainblocks.dto.request;

public class SubmitAnswerRequest {

    private Long playerId;
    private Integer answer;

    public Long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(Long playerId) {
        this.playerId = playerId;
    }

    public Integer getAnswer() {
        return answer;
    }

    public void setAnswer(Integer answer) {
        this.answer = answer;
    }
}
