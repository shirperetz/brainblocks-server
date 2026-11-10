package com.example.brainblocks.dto.request;

public class DecisionRequest {

    private Long participantId;
    private String choice;

    public Long getParticipantId() { return participantId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public String getChoice() { return choice; }
    public void setChoice(String choice) { this.choice = choice; }
}
