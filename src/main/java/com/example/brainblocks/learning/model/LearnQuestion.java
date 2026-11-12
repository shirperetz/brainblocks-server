package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-12 (Shir: "the exercises are saved in a bank, and new ones every time - endless"): every
 * question a child is served is kept here - what was asked, the options, the right answer (which now
 * stays on the server until the child answers), the skill, and what the child chose. The server
 * checks answers from this row, so the phone never decides "right" by itself.
 */
@Entity
@Table(name = "learn_question", indexes = {
        @Index(name = "ix_learn_question_profile", columnList = "profileId, createdAt")})
public class LearnQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long profileId;

    @Column(length = 20)
    private String topic;

    private int stageNumber;

    @Column(length = 4)
    private String grade;

    @Column(nullable = false, length = 500)
    private String text;

    /** The four options, joined with \u001F (they never contain it). */
    @Column(nullable = false, length = 600)
    private String options;

    @Column(nullable = false, length = 120)
    private String correctAnswer;

    @Column(length = 40)
    private String skill;

    @Column(length = 1000)
    private String explanation;

    private LocalDateTime createdAt;

    /** The child's FIRST answer (a retry later doesn't overwrite it). */
    @Column(length = 120)
    private String chosenAnswer;

    private Boolean correct;

    private LocalDateTime answeredAt;

    protected LearnQuestion() {}

    public LearnQuestion(Long profileId, String topic, int stageNumber, String grade, String text,
                         java.util.List<String> options, String correctAnswer, String skill, String explanation) {
        this.profileId = profileId;
        this.topic = topic;
        this.stageNumber = stageNumber;
        this.grade = grade;
        this.text = cut(text, 500);
        this.options = cut(String.join("\u001F", options), 600);
        this.correctAnswer = cut(correctAnswer, 120);
        this.skill = skill;
        this.explanation = cut(explanation, 1000);
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    /** Records the first answer; returns whether THIS answer is right. */
    public boolean answer(String chosen) {
        boolean right = correctAnswer.equals(chosen == null ? null : chosen.trim());
        if (answeredAt == null) {
            chosenAnswer = cut(chosen, 120);
            correct = right;
            answeredAt = LocalDateTime.now();
        }
        return right;
    }

    public java.util.List<String> optionList() {
        return java.util.List.of(options.split("\u001F", -1));
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getTopic() { return topic; }
    public int getStageNumber() { return stageNumber; }
    public String getText() { return text; }
    public String getCorrectAnswer() { return correctAnswer; }
    public String getSkill() { return skill; }
    public String getExplanation() { return explanation; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getChosenAnswer() { return chosenAnswer; }
    public Boolean getCorrect() { return correct; }
    public LocalDateTime getAnsweredAt() { return answeredAt; }
}
