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
 * 2026-11-11: a class a teacher (or the admin) opens - "ה׳2". Children sign up with the class's own
 * join code, wait for that teacher's OK, and only she (and the admin) sees them. Never deleted:
 * closing it only stops new sign-ups.
 */
@Entity
@Table(name = "learn_class")
public class LearnClass {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    /** The teacher's profile id (the admin's own classes: hers). */
    @Column(nullable = false)
    private Long teacherId;

    @Column(nullable = false, unique = true, length = 20)
    private String joinCode;

    private Boolean closed;

    /** 2026-11-12: the grade this class practices; its children get it, locked. null = not set. */
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(length = 8)
    private Grade grade;

    public Grade getGrade() { return grade; }
    public void setGrade(Grade grade) { this.grade = grade; }

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected LearnClass() {}

    public LearnClass(String name, Long teacherId, String joinCode) {
        this.name = name;
        this.teacherId = teacherId;
        this.joinCode = joinCode;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getTeacherId() { return teacherId; }
    public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
    public String getJoinCode() { return joinCode; }
    public void setJoinCode(String joinCode) { this.joinCode = joinCode; }
    public boolean isClosed() { return Boolean.TRUE.equals(closed); }
    public void setClosed(boolean closed) { this.closed = closed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
