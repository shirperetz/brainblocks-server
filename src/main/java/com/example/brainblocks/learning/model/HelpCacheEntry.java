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
 * 2026-11-11 (Shir: "every time children say לא הבנתי, remember the exercise and the help, so the AI
 * isn't used again and again"). One explanation the local model wrote AND that passed every check,
 * kept by: the exercise, the wrong answer, which press it was, the grade. The same press on the same
 * exercise later reads it from here - no model call. Only checked replies are ever stored.
 */
@Entity
@Table(name = "learn_help_cache")
public class HelpCacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 of question|chosen|attempt|grade - unique. */
    @Column(nullable = false, unique = true, length = 64)
    private String cacheKey;

    @Column(nullable = false, length = 200)
    private String question;

    @Column(length = 20)
    private String chosen;

    @Column(nullable = false)
    private int attempt;

    @Column(length = 4)
    private String grade;

    @Column(nullable = false, length = 600)
    private String text;

    @Column(nullable = false)
    private int hits;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;

    protected HelpCacheEntry() {}

    public HelpCacheEntry(String cacheKey, String question, String chosen, int attempt, String grade, String text) {
        this.cacheKey = cacheKey;
        this.question = question;
        this.chosen = chosen;
        this.attempt = attempt;
        this.grade = grade;
        this.text = text.length() > 600 ? text.substring(0, 600) : text;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        lastUsedAt = createdAt;
    }

    public void used() {
        hits++;
        lastUsedAt = LocalDateTime.now();
    }

    public String getCacheKey() { return cacheKey; }
    public String getQuestion() { return question; }
    public String getText() { return text; }
    public int getHits() { return hits; }
    public int getAttempt() { return attempt; }
}
