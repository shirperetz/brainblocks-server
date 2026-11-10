package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RaceQuestion;

import java.time.LocalDateTime;

public class RaceQuestionResponse {

    private final Long id;
    private final String roomCode;
    private final String questionText;
    private final QuestionOperation operation;
    private final QuestionDifficulty difficulty;
    private final int timeLimit;
    private final boolean isDecisionQuestion;
    private final boolean isDirtRoadQuestion;
    private final LocalDateTime createdAt;

    public RaceQuestionResponse(Long id, String roomCode, String questionText,
                                 QuestionOperation operation, QuestionDifficulty difficulty,
                                 int timeLimit, boolean isDecisionQuestion, boolean isDirtRoadQuestion,
                                 LocalDateTime createdAt) {
        this.id = id;
        this.roomCode = roomCode;
        this.questionText = questionText;
        this.operation = operation;
        this.difficulty = difficulty;
        this.timeLimit = timeLimit;
        this.isDecisionQuestion = isDecisionQuestion;
        this.isDirtRoadQuestion = isDirtRoadQuestion;
        this.createdAt = createdAt;
    }

    public static RaceQuestionResponse fromEntity(RaceQuestion q) {
        return new RaceQuestionResponse(
                q.getId(),
                q.getRaceRoom().getRoomCode(),
                q.getQuestionText(),
                q.getOperation(),
                q.getDifficulty(),
                q.getTimeLimit(),
                q.isDecisionQuestion(),
                q.isDirtRoadQuestion(),
                q.getCreatedAt()
        );
    }

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public String getQuestionText() { return questionText; }
    public QuestionOperation getOperation() { return operation; }
    public QuestionDifficulty getDifficulty() { return difficulty; }
    public int getTimeLimit() { return timeLimit; }
    public boolean isDecisionQuestion() { return isDecisionQuestion; }
    public boolean isDirtRoadQuestion() { return isDirtRoadQuestion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
