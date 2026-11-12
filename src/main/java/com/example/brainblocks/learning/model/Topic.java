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
    // 2026-11-11: שברים (ה׳-ו׳) וגיאומטריה (ו׳) - FractionGeometryQuestions.
    FRACTIONS,
    GEOMETRY,
    // 2026-11-11 (Shir): geometry as three tracks - זוויות, צלעות והיקף, שטח. GEOMETRY = all of them (mixed).
    ANGLES,
    ANGLE_CALC, // 2026-11-11: חישוב זוויות, separate from recognizing angles
    PERIMETER,
    AREA,
    // "אי המספרים" mini-games (2026-11-10): questions are made in the kids client, the server keeps stars/progress.
    DRAGON,
    BALLOONS,
    BRIDGE,
    TRAIN,
    // 2026-11-12 (Shir: "more islands for grades 1-3"): 🕐 the clock-and-money island.
    CLOCK,
    SHOP,
    COINS;

    /** True for the island mini-games, whose questions are generated client-side. */
    public boolean isGame() {
        return this == DRAGON || this == BALLOONS || this == BRIDGE || this == TRAIN
                || this == CLOCK || this == SHOP || this == COINS;
    }
}
