package com.example.brainblocks.learning.model;

/** A practice subject. Matches the topic cards in the kids client. */
public enum Topic {
    ADDITION,
    SUBTRACTION,
    MULTIPLICATION,
    DIVISION,
    COMPARISON,
    SEQUENCES,
    WORD_PROBLEMS,
    MIXED,
    // "אי המספרים" mini-games (2026-11-10): questions are made in the kids client, the server keeps stars/progress.
    DRAGON,
    BALLOONS,
    BRIDGE,
    TRAIN;

    /** True for the island mini-games, whose questions are generated client-side. */
    public boolean isGame() {
        return this == DRAGON || this == BALLOONS || this == BRIDGE || this == TRAIN;
    }
}
