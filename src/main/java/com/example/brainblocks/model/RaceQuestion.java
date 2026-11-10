package com.example.brainblocks.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "race_questions")
public class RaceQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_room_id", nullable = false)
    private RaceRoom raceRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private RacePlayer participant;

    @Column(nullable = false, length = 200)
    private String questionText;

    @Column(nullable = false)
    private int correctAnswer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuestionDifficulty difficulty;

    @Column(nullable = false)
    private int timeLimit;

    @Column(nullable = false)
    private boolean answered;

    @Column
    private LocalDateTime answeredAt;

    @Column(nullable = false)
    private boolean isDecisionQuestion;

    @Column(nullable = false)
    private boolean isDirtRoadQuestion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RaceQuestion() {}

    public RaceQuestion(RaceRoom raceRoom, RacePlayer participant, String questionText,
                        int correctAnswer, QuestionOperation operation, QuestionDifficulty difficulty,
                        int timeLimit, boolean isDecisionQuestion, boolean isDirtRoadQuestion) {
        this.raceRoom = raceRoom;
        this.participant = participant;
        this.questionText = questionText;
        this.correctAnswer = correctAnswer;
        this.operation = operation;
        this.difficulty = difficulty;
        this.timeLimit = timeLimit;
        this.answered = false;
        this.isDecisionQuestion = isDecisionQuestion;
        this.isDirtRoadQuestion = isDirtRoadQuestion;
    }

    public void markAnswered() {
        this.answered = true;
        this.answeredAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public RaceRoom getRaceRoom() { return raceRoom; }
    public RacePlayer getParticipant() { return participant; }
    public String getQuestionText() { return questionText; }
    public int getCorrectAnswer() { return correctAnswer; }
    public QuestionOperation getOperation() { return operation; }
    public QuestionDifficulty getDifficulty() { return difficulty; }
    public int getTimeLimit() { return timeLimit; }
    public boolean isAnswered() { return answered; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
    public boolean isDecisionQuestion() { return isDecisionQuestion; }
    public boolean isDirtRoadQuestion() { return isDirtRoadQuestion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
