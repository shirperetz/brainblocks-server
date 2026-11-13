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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 2026-11-12 (idea #5, "weekly task from the teacher"): "this week: 3 rounds of fractions" for a whole
 * class. Progress is not stored - it is counted from the children's finished STAGE rounds of that topic
 * between startDate and endDate (a round counts when at least half its answers were right).
 * Never deleted: cancelling only hides it from the children.
 */
@Entity
@Table(name = "learn_assignment")
public class LearnAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long classId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Topic topic;

    @Column(nullable = false)
    private int rounds;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Inclusive. */
    @Column(nullable = false)
    private LocalDate endDate;

    /** Who set it (the teacher, or the admin). */
    @Column(nullable = false)
    private Long createdBy;

    private Boolean cancelled;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 2026-11-13 (Shir: "practice from the report"): a task for ONE child (the teacher taps "practice
     * this" next to a mistake in the child's report). null = the whole class. classId is then the
     * child's class, or 0 for a child of no class.
     */
    private Long childId;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }

    protected LearnAssignment() {}

    public LearnAssignment(Long classId, Topic topic, int rounds, LocalDate startDate, LocalDate endDate, Long createdBy) {
        this.classId = classId;
        this.topic = topic;
        this.rounds = rounds;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** Running on that day (not cancelled, the day inside the dates). */
    public boolean isActiveOn(LocalDate day) {
        return !isCancelled() && !day.isBefore(startDate) && !day.isAfter(endDate);
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public Topic getTopic() { return topic; }
    public int getRounds() { return rounds; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Long getCreatedBy() { return createdBy; }
    public boolean isCancelled() { return Boolean.TRUE.equals(cancelled); }
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
