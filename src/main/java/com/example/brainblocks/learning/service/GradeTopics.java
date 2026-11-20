package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-12: which topics belong to which grade - the same table as the client (learn-client
 * src/data/topics.js TOPICS_BY_GRADE), enforced here for a child whose grade is locked: another
 * grade's topic gives no questions, whatever the screen sends.
 */
public final class GradeTopics {

    private GradeTopics() {}

    private static final Set<Topic> FIRST = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.COMPARISON,
            Topic.SEQUENCES, Topic.MIXED, Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN,
            Topic.CLOCK, Topic.SHOP, Topic.COINS, Topic.SHAPE_NAME, Topic.SHAPE_SIDES, Topic.SHAPE_PATTERN);
    // 2026-11-12 (curriculum by grade): ב׳ - multiplication/division as groups (2/5/10); ג׳ - unit
    // fractions, angles, sides and perimeter; ד׳ - + area (rectangle/square). Each at its grade's level.
    private static final Set<Topic> SECOND = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.COMPARISON,
            Topic.SEQUENCES, Topic.MIXED, Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN,
            Topic.MULTIPLICATION, Topic.DIVISION, Topic.CLOCK, Topic.SHOP, Topic.COINS,
            Topic.SHAPE_NAME, Topic.SHAPE_SIDES, Topic.SHAPE_PATTERN, Topic.GROUPS, Topic.SHARING, Topic.ARRAYS);
    private static final Set<Topic> THIRD = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION,
            Topic.DIVISION, Topic.COMPARISON, Topic.SEQUENCES, Topic.MIXED, Topic.FRACTIONS, Topic.ANGLES, Topic.PERIMETER,
            // 2026-11-12: ג׳ also plays on the islands (Shir) - the clock-and-money island is ג׳ level too.
            Topic.CLOCK, Topic.SHOP, Topic.COINS,
            // 2026-11-13: and the shapes, times and big-numbers islands.
            Topic.SHAPE_NAME, Topic.SHAPE_SIDES, Topic.SHAPE_PATTERN, Topic.GROUPS, Topic.SHARING, Topic.ARRAYS,
            Topic.PLACE_VALUE, Topic.NUMBER_LINE, Topic.BIGGER);
    private static final Set<Topic> FOURTH = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION,
            Topic.DIVISION, Topic.COMPARISON, Topic.SEQUENCES, Topic.WORD_PROBLEMS, Topic.MIXED,
            Topic.FRACTIONS, Topic.ANGLES, Topic.PERIMETER, Topic.AREA);
    private static final Set<Topic> UPPER = EnumSet.noneOf(Topic.class);
    static {
        for (Topic t : Topic.values()) if (!t.isGame()) UPPER.add(t);
    }

    private static final Map<Grade, Set<Topic>> BY_GRADE = Map.of(
            Grade.G1, FIRST, Grade.G2, SECOND, Grade.G3, THIRD, Grade.G4, FOURTH, Grade.G5, UPPER, Grade.G6, UPPER);

    public static boolean allowed(Topic topic, Grade grade) {
        if (grade == null || topic == null) return true;
        // 2026-11-21: the new topics - a grade has them when it has at least one of their levels.
        if (ExtraTopicsQuestions.handles(topic)) return ExtraTopicsQuestions.levelCap(topic, grade) > 0;
        return BY_GRADE.getOrDefault(grade, UPPER).contains(topic);
    }
}
