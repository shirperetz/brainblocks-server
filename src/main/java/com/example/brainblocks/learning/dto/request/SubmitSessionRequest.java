package com.example.brainblocks.learning.dto.request;

import java.util.List;

/** Body for {@code POST /api/learn/sessions} — a finished practice run. */
public class SubmitSessionRequest {

    private Long profileId;
    private String topic;
    private int stage;
    private String mode;       // STAGE | DAILY | TIMED (defaults to STAGE)
    private long durationMs;
    private List<AnswerDto> answers;

    public Long getProfileId() { return profileId; }
    public void setProfileId(Long profileId) { this.profileId = profileId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public int getStage() { return stage; }
    public void setStage(int stage) { this.stage = stage; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public List<AnswerDto> getAnswers() { return answers; }

    /** 2026-11-21 (finding the gap): a foundations round (Foundations key) - saved, not on the map. */
    private String foundation;

    public String getFoundation() { return foundation; }
    public void setFoundation(String foundation) { this.foundation = foundation; }
    public void setAnswers(List<AnswerDto> answers) { this.answers = answers; }

    /** 2026-11-26: the phone's own key for this round - a resend returns the first save (RoundOnce). */
    private String roundKey;

    public String getRoundKey() { return roundKey; }
    public void setRoundKey(String roundKey) { this.roundKey = roundKey; }
}
