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
            Topic.SEQUENCES, Topic.MIXED, Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN);
    private static final Set<Topic> THIRD = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION,
            Topic.DIVISION, Topic.COMPARISON, Topic.SEQUENCES, Topic.MIXED);
    private static final Set<Topic> FOURTH = EnumSet.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION,
            Topic.DIVISION, Topic.COMPARISON, Topic.SEQUENCES, Topic.WORD_PROBLEMS, Topic.MIXED);
    private static final Set<Topic> UPPER = EnumSet.complementOf(EnumSet.of(Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN));

    private static final Map<Grade, Set<Topic>> BY_GRADE = Map.of(
            Grade.G1, FIRST, Grade.G2, FIRST, Grade.G3, THIRD, Grade.G4, FOURTH, Grade.G5, UPPER, Grade.G6, UPPER);

    public static boolean allowed(Topic topic, Grade grade) {
        if (grade == null || topic == null) return true;
        return BY_GRADE.getOrDefault(grade, UPPER).contains(topic);
    }
}
