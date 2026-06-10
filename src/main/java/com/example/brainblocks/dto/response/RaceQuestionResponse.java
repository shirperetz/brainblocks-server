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
    private final LocalDateTime createdAt;

    public RaceQuestionResponse(
            Long id,
            String roomCode,
            String questionText,
            QuestionOperation operation,
            QuestionDifficulty difficulty,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.roomCode = roomCode;
        this.questionText = questionText;
        this.operation = operation;
        this.difficulty = difficulty;
        this.createdAt = createdAt;
    }

    public static RaceQuestionResponse fromEntity(RaceQuestion raceQuestion) {
        return new RaceQuestionResponse(
                raceQuestion.getId(),
                raceQuestion.getRaceRoom().getRoomCode(),
                raceQuestion.getQuestionText(),
                raceQuestion.getOperation(),
                raceQuestion.getDifficulty(),
                raceQuestion.getCreatedAt()
        );
    }

    public Long getId() {
        return id;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getQuestionText() {
        return questionText;
    }

    public QuestionOperation getOperation() {
        return operation;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
