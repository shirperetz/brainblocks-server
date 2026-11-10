package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A question the child got wrong. Powers the "בוא נחזור על מה שהיה קשה" review screen.
 * Marked resolved once the child answers it correctly on a retry.
 */
@Entity
@Table(name = "learn_mistake")
public class LearnerMistake {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    // varchar, not a MySQL ENUM: a new Topic value must not need an ALTER (2026-11-10)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Topic topic;

    @Column(nullable = false, length = 160)
    private String questionText;

    @Column(length = 40)
    private String givenAnswer;

    @Column(nullable = false, length = 40)
    private String correctAnswer;

    @Column(length = 240)
    private String explanation;

    @Column(nullable = false)
    private boolean resolved;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearnerMistake() {}

    public LearnerMistake(Long profileId, Topic topic, String questionText,
                          String givenAnswer, String correctAnswer, String explanation) {
        this.profileId = profileId;
        this.topic = topic;
        this.questionText = questionText;
        this.givenAnswer = givenAnswer;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
        this.resolved = false;
    }

    public void resolve() {
        this.resolved = true;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public Topic getTopic() { return topic; }
    public String getQuestionText() { return questionText; }
    public String getGivenAnswer() { return givenAnswer; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getExplanation() { return explanation; }
    public boolean isResolved() { return resolved; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
