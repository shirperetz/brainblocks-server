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
    COINS,
    // 2026-11-13 (Shir: "the 3 missing islands"): 🔷 shapes (א׳-ג׳), ✖️ times (ב׳-ג׳), 🏰 big numbers (ג׳).
    SHAPE_NAME,
    SHAPE_SIDES,
    SHAPE_PATTERN,
    GROUPS,
    SHARING,
    ARRAYS,
    PLACE_VALUE,
    NUMBER_LINE,
    BIGGER,
    // 2026-11-21 (Shir, overnight: "all the material א׳-ו׳, by the curriculum and the מיצ"ב") -
    // ExtraTopicsQuestions; each grade gets only its levels (levelCap).
    EVEN_ODD,
    MAKE_TEN,
    ORDER_OPS,
    ROUNDING,
    DIVISIBILITY,
    MEASURE,
    DECIMALS,
    PERCENT,
    RATIO,
    DATA,
    CIRCLE,
    VOLUME,
    EQUATIONS;

    /** True for the island mini-games, whose questions are generated client-side. */
    public boolean isGame() {
        return switch (this) {
            case DRAGON, BALLOONS, BRIDGE, TRAIN, CLOCK, SHOP, COINS,
                 SHAPE_NAME, SHAPE_SIDES, SHAPE_PATTERN, GROUPS, SHARING, ARRAYS, PLACE_VALUE, NUMBER_LINE, BIGGER -> true;
            default -> false;
        };
    }
}
