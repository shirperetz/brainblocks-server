package com.example.brainblocks.dto.response;

import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionNumberType;
import com.example.brainblocks.model.QuestionOperation;
import com.example.brainblocks.model.RaceQuestionSettings;

import java.util.Set;

public class QuestionSettingsResponse {

    private final String roomCode;
    private final Set<QuestionOperation> operations;
    private final Set<QuestionNumberType> numberTypes;
    private final QuestionDifficulty difficulty;

    public QuestionSettingsResponse(
            String roomCode,
            Set<QuestionOperation> operations,
            Set<QuestionNumberType> numberTypes,
            QuestionDifficulty difficulty
    ) {
        this.roomCode = roomCode;
        this.operations = operations;
        this.numberTypes = numberTypes;
        this.difficulty = difficulty;
    }

    public static QuestionSettingsResponse fromEntity(RaceQuestionSettings settings) {
        return new QuestionSettingsResponse(
                settings.getRaceRoom().getRoomCode(),
                settings.getOperations(),
                settings.getNumberTypes(),
                settings.getDifficulty()
        );
    }

    public String getRoomCode() {
        return roomCode;
    }

    public Set<QuestionOperation> getOperations() {
        return operations;
    }

    public Set<QuestionNumberType> getNumberTypes() {
        return numberTypes;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }
}
