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

    // 2026-11-12 (idea #4 - review again after a day, 3 days, a week): one right answer in the review no
    // longer ends it. Right -> back in 1, then 3, then 7 days; right the 4th time -> learned (resolved).
    // Wrong in the review -> back tomorrow, from the start. null = due now (a fresh mistake).
    static final int[] GAP_DAYS = {1, 3, 7};

    private Integer reviewStep;
    private LocalDateTime nextReviewAt;

    public int getReviewStep() { return reviewStep == null ? 0 : reviewStep; }
    public LocalDateTime getNextReviewAt() { return nextReviewAt; }

    public boolean isDue(LocalDateTime now) {
        return !resolved && (nextReviewAt == null || !nextReviewAt.isAfter(now));
    }

    public void reviewedRight(LocalDateTime now) {
        int step = getReviewStep();
        if (step >= GAP_DAYS.length) {
            resolved = true;
            return;
        }
        nextReviewAt = now.plusDays(GAP_DAYS[step]);
        reviewStep = step + 1;
    }

    public void reviewedWrong(LocalDateTime now) {
        reviewStep = 0;
        nextReviewAt = now.plusDays(1);
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
