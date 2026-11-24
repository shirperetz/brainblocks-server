package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.Topic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 2026-11-21 (Shir chose "finding the gap - treat the root, not the exercise"): a child who keeps
 * failing a topic often misses something UNDER it - long multiplication fails on the times table,
 * fractions on division, subtraction with borrowing on the facts up to 20. When a topic goes badly
 * this looks at the base skills under it: no evidence -> a short 5-question check; weak -> a short
 * practice of that base; strong -> the next base. The child sees "building foundations: the times
 * table", never "you went back to grade 3". Built only from answers the server already judged.
 */
public final class Foundations {

    private Foundations() {}

    /** One base skill: practiced as [topic] at [grade]'s range, level [stage], tagged [skill]. */
    public record Base(String key, String skill, String name, String emoji, Topic topic, Grade grade, int stage,
                       Grade minGrade, Set<String> evidence) {}

    public static final Base ADD20 = new Base("ADD20", "BASE_ADD20", "חיבור עד 20", "➕", Topic.ADDITION, Grade.G1, 6,
            Grade.G2, Set.of("BASE_ADD20", "ADD_TO_10", "ISL_ADD_10", "ISL_CROSS_10", "MAKE_10"));
    public static final Base SUB20 = new Base("SUB20", "BASE_SUB20", "חיסור עד 20", "➖", Topic.SUBTRACTION, Grade.G1, 6,
            Grade.G2, Set.of("BASE_SUB20", "SUB_TO_10", "ISL_SUB_10"));
    public static final Base TIMES = new Base("TIMES", "BASE_TIMES", "לוח הכפל", "✖️", Topic.MULTIPLICATION, Grade.G3, 6,
            Grade.G3, Set.of("BASE_TIMES", "MUL_SMALL", "MUL_TABLE"));
    public static final Base DIVIDE = new Base("DIVIDE", "BASE_DIVIDE", "חילוק בלוח הכפל", "➗", Topic.DIVISION, Grade.G3, 6,
            Grade.G4, Set.of("BASE_DIVIDE", "DIV_TABLE"));

    public static final List<Base> ALL = List.of(ADD20, SUB20, TIMES, DIVIDE);

    /** What to look under each topic, first things first. */
    static final Map<Topic, List<Base>> UNDER = new LinkedHashMap<>();

    static {
        UNDER.put(Topic.ADDITION, List.of(ADD20));
        UNDER.put(Topic.SUBTRACTION, List.of(ADD20, SUB20));
        UNDER.put(Topic.MULTIPLICATION, List.of(ADD20, TIMES));
        UNDER.put(Topic.DIVISION, List.of(TIMES, DIVIDE));
        UNDER.put(Topic.WORD_PROBLEMS, List.of(ADD20, SUB20, TIMES));
        UNDER.put(Topic.FRACTIONS, List.of(TIMES, DIVIDE));
        UNDER.put(Topic.AREA, List.of(TIMES));
        UNDER.put(Topic.PERIMETER, List.of(ADD20));
        UNDER.put(Topic.ANGLE_CALC, List.of(ADD20, SUB20));
        UNDER.put(Topic.DIVISIBILITY, List.of(TIMES, DIVIDE));
        UNDER.put(Topic.ORDER_OPS, List.of(TIMES));
        UNDER.put(Topic.EQUATIONS, List.of(ADD20, SUB20, TIMES));
        UNDER.put(Topic.PERCENT, List.of(TIMES, DIVIDE));
        UNDER.put(Topic.RATIO, List.of(TIMES, DIVIDE));
        UNDER.put(Topic.FRAC_EXPAND, List.of(TIMES));
        UNDER.put(Topic.DECIMALS, List.of(ADD20, SUB20));
        UNDER.put(Topic.VOLUME, List.of(TIMES));
    }

    /** A topic is "going badly": at least this many recent answers, under WEAK_PCT right. */
    static final int TOPIC_MIN = 10;
    static final int TOPIC_LAST = 20;
    static final int WEAK_PCT = 60;
    /** A base: at least CHECK_MIN answers to judge it; strong = OK_PCT of the last BASE_LAST. */
    static final int CHECK_MIN = 5;
    static final int BASE_LAST = 10;
    static final int OK_PCT = 80;

    public static boolean isBaseSkill(String skill) {
        return skill != null && skill.startsWith("BASE_");
    }

    public static Base byKey(String key) {
        for (Base b : ALL) if (b.key().equalsIgnoreCase(String.valueOf(key))) return b;
        return null;
    }

    /**
     * The gap to work on now, or null. [recent] = the child's answers, newest first (any topics).
     * Returns { key, name, emoji, status: CHECK|PRACTICE, forTopic, topicPct, pct, answers }.
     */
    public static Map<String, Object> find(List<LearnerAnswer> recent, Grade grade) {
        if (recent == null || recent.isEmpty()) return null;
        Grade g = grade == null ? Grade.G3 : grade;
        // the weakest topic that has a base under it
        Topic weakest = null;
        int weakestPct = 101;
        for (Topic t : UNDER.keySet()) {
            List<LearnerAnswer> mine = new ArrayList<>();
            for (LearnerAnswer a : recent) {
                if (a.getTopic() == t && !isBaseSkill(a.getSkill())) mine.add(a);
                if (mine.size() >= TOPIC_LAST) break;
            }
            if (mine.size() < TOPIC_MIN) continue;
            int pct = pct(mine);
            if (pct < WEAK_PCT && pct < weakestPct) {
                weakest = t;
                weakestPct = pct;
            }
        }
        if (weakest == null) return null;
        for (Base b : UNDER.get(weakest)) {
            if (g.ordinal() < b.minGrade().ordinal()) continue; // a base this grade is still learning
            if (b.topic() == weakest && b.stage() >= 6 && b.grade() == g) continue; // the base IS the topic here
            List<LearnerAnswer> ev = new ArrayList<>();
            for (LearnerAnswer a : recent) {
                if (b.evidence().contains(a.getSkill())) ev.add(a);
                if (ev.size() >= BASE_LAST) break;
            }
            String status;
            if (ev.size() < CHECK_MIN) status = "CHECK";
            else if (pct(ev) < OK_PCT) status = "PRACTICE";
            else continue; // strong - look at the next base
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("key", b.key());
            out.put("name", b.name());
            out.put("emoji", b.emoji());
            out.put("status", status);
            out.put("forTopic", weakest.name());
            out.put("topicPct", weakestPct);
            out.put("pct", ev.isEmpty() ? null : pct(ev));
            out.put("answers", ev.size());
            return out;
        }
        return null;
    }

    private static int pct(List<LearnerAnswer> rows) {
        if (rows.isEmpty()) return 0;
        long right = rows.stream().filter(LearnerAnswer::isCorrect).count();
        return (int) Math.round(right * 100.0 / rows.size());
    }
}
