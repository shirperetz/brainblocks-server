package com.example.brainblocks.dto.request;

import com.example.brainblocks.model.QuestionDifficulty;
import com.example.brainblocks.model.QuestionNumberType;
import com.example.brainblocks.model.QuestionOperation;

import java.util.Set;

public class UpdateQuestionSettingsRequest {

    private Set<QuestionOperation> operations;
    private Set<QuestionNumberType> numberTypes;
    private QuestionDifficulty difficulty;

    public Set<QuestionOperation> getOperations() {
        return operations;
    }

    public void setOperations(Set<QuestionOperation> operations) {
        this.operations = operations;
    }

    public Set<QuestionNumberType> getNumberTypes() {
        return numberTypes;
    }

    public void setNumberTypes(Set<QuestionNumberType> numberTypes) {
        this.numberTypes = numberTypes;
    }

    public QuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(QuestionDifficulty difficulty) {
        this.difficulty = difficulty;
    }
}
