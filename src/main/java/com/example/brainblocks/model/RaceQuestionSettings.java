package com.example.brainblocks.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "race_question_settings")
public class RaceQuestionSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false, unique = true)
    private RaceRoom raceRoom;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "race_question_setting_operations",
            joinColumns = @JoinColumn(name = "settings_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 30)
    private Set<QuestionOperation> operations = EnumSet.noneOf(QuestionOperation.class);

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "race_question_setting_number_types",
            joinColumns = @JoinColumn(name = "settings_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "number_type", nullable = false, length = 30)
    private Set<QuestionNumberType> numberTypes = EnumSet.noneOf(QuestionNumberType.class);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionDifficulty difficulty;

    protected RaceQuestionSettings() {
    }

    public RaceQuestionSettings(
            RaceRoom raceRoom,
            Set<QuestionOperation> operations,
            Set<QuestionNumberType> numberTypes,
            QuestionDifficulty difficulty
    ) {
        this.raceRoom = raceRoom;
        update(operations, numberTypes, difficulty);
    }

    public void update(
            Set<QuestionOperation> operations,
            Set<QuestionNumberType> numberTypes,
            QuestionDifficulty difficulty
    ) {
        this.operations = EnumSet.copyOf(operations);
        this.numberTypes = EnumSet.copyOf(numberTypes);
        this.difficulty = difficulty;
    }

    public Long getId() {
        return id;
    }

    public RaceRoom getRaceRoom() {
        return raceRoom;
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
