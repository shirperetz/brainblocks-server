package com.example.brainblocks.learning.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-22 (Shir's room upgrade #10): a room the teacher saved, opened again with one tap - the class,
 * the topics, the minutes, coins on/off, groups on/off. Only its teacher sees it.
 */
@Entity
@Table(name = "learn_room_template", indexes = @Index(name = "ix_room_template_teacher", columnList = "teacherId"))
public class LearnRoomTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long teacherId;

    private String name;

    private Long classId;

    private String grade;

    /** comma separated topic keys (and "MY"). */
    private String topics;

    private int minutes;

    private boolean coins;

    private boolean teams;

    private LocalDateTime createdAt;

    protected LearnRoomTemplate() {}

    public LearnRoomTemplate(Long teacherId, String name, Long classId, String grade, String topics, int minutes, boolean coins, boolean teams) {
        this.teacherId = teacherId;
        this.name = name;
        this.classId = classId;
        this.grade = grade;
        this.topics = topics;
        this.minutes = minutes;
        this.coins = coins;
        this.teams = teams;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getTeacherId() { return teacherId; }
    public String getName() { return name; }
    public Long getClassId() { return classId; }
    public String getGrade() { return grade; }
    public String getTopics() { return topics; }
    public int getMinutes() { return minutes; }
    public boolean isCoins() { return coins; }
    public boolean isTeams() { return teams; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
