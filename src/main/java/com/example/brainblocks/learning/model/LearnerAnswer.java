package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-11 (Shir: "what she finds hard and what she doesn't"): one answered question - right or
 * wrong - with the skill it practiced. Feeds the progress screen (strong / on the way / hard per
 * skill, trend by week). Before this only the wrong answers were kept (learn_mistake).
 */
@Entity
@Table(name = "learn_answer", indexes = @Index(name = "idx_answer_profile", columnList = "profileId"))
public class LearnerAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Topic topic;

    @Column(nullable = false, length = 40)
    private String skill;

    @Column(nullable = false)
    private int stageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SessionMode mode;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private boolean hintUsed;

    @Column(length = 160)
    private String questionText;

    @Column(length = 40)
    private String givenAnswer;

    @Column(length = 40)
    private String correctAnswer;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearnerAnswer() {}

    public LearnerAnswer(Long profileId, Topic topic, String skill, int stageNumber, SessionMode mode,
                         boolean correct, boolean hintUsed, String questionText, String givenAnswer,
                         String correctAnswer) {
        this.profileId = profileId;
        this.topic = topic;
        this.skill = skill;
        this.stageNumber = stageNumber;
        this.mode = mode;
        this.correct = correct;
        this.hintUsed = hintUsed;
        this.questionText = cut(questionText, 160);
        this.givenAnswer = cut(givenAnswer, 40);
        this.correctAnswer = cut(correctAnswer, 40);
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Tests only: an answer from an earlier week. */
    public LearnerAnswer at(LocalDateTime when) {
        this.createdAt = when;
        return this;
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public Topic getTopic() { return topic; }
    public String getSkill() { return skill; }
    public int getStageNumber() { return stageNumber; }
    public SessionMode getMode() { return mode; }
    public boolean isCorrect() { return correct; }
    public boolean isHintUsed() { return hintUsed; }
    public String getQuestionText() { return questionText; }
    public String getGivenAnswer() { return givenAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
