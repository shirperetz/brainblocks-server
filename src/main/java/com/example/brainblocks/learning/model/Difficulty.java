package com.example.brainblocks.learning.model;

/**
 * Difficulty band within a grade (קל / בינוני / קשה). Adaptive difficulty nudges
 * a child up or down within this scale, never outside their grade.
 */
public enum Difficulty {
    EASY,
    MEDIUM,
    HARD
}
