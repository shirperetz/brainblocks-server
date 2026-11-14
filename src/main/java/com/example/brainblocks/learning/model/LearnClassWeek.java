package com.example.brainblocks.learning.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/**
 * 2026-11-14 (Shir: "a class island, in 3D like the children's, upgraded"): one finished week of a
 * class - how much it built and whether the building was completed. Written once, when the week is
 * over and someone opens the class island, so the history is never recounted.
 */
@Entity
@Table(name = "learn_class_week", uniqueConstraints = @UniqueConstraint(name = "uq_class_week", columnNames = {"classId", "weekStart"}))
public class LearnClassWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long classId;

    private LocalDate weekStart;

    private int built;

    private int target;

    private boolean done;

    protected LearnClassWeek() {}

    public LearnClassWeek(Long classId, LocalDate weekStart, int built, int target, boolean done) {
        this.classId = classId;
        this.weekStart = weekStart;
        this.built = built;
        this.target = target;
        this.done = done;
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public LocalDate getWeekStart() { return weekStart; }
    public int getBuilt() { return built; }
    public int getTarget() { return target; }
    public boolean isDone() { return done; }
}
