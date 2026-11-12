package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-12 (Shir: "the teacher can add things, even her own exercises, and it's saved"): an exercise a
 * teacher (or the admin) wrote, for her game rooms. Never deleted - switching it off hides it.
 */
@Entity
@Table(name = "teacher_question")
public class TeacherQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long teacherId;

    @Column(nullable = false, length = 160)
    private String text;

    @Column(nullable = false, length = 40)
    private String correct;

    @Column(length = 40) private String wrong1;
    @Column(length = 40) private String wrong2;
    @Column(length = 40) private String wrong3;

    private Boolean off;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TeacherQuestion() {}

    public TeacherQuestion(Long teacherId, String text, String correct, String wrong1, String wrong2, String wrong3) {
        this.teacherId = teacherId;
        this.text = text;
        this.correct = correct;
        this.wrong1 = wrong1;
        this.wrong2 = wrong2;
        this.wrong3 = wrong3;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public String getText() { return text; }
    public String getCorrect() { return correct; }
    public String getWrong1() { return wrong1; }
    public String getWrong2() { return wrong2; }
    public String getWrong3() { return wrong3; }
    public boolean isOff() { return Boolean.TRUE.equals(off); }
    public void setOff(boolean off) { this.off = off; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
