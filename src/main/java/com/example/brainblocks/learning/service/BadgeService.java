package com.example.brainblocks.learning.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-27 (Shir: idea #6 - badges). Which badges a child earned, worked out from data they already
 * have: total stars, days in a row, correct answers, stages finished, buildings on the island, teacher
 * prizes. Pure (no database) so it is easy to test; the controller supplies the counts.
 */
public final class BadgeService {

    private BadgeService() {}

    /** A badge the child can earn. need = the number to reach for the given signal. */
    public record Badge(String key, String emoji, String name, String signal, int need) {}

    // Order = how they show. Tiers of the same signal (streak 3/7/30…) all appear.
    static final List<Badge> ALL = List.of(
            new Badge("first_star", "⭐", "הכוכב הראשון", "stars", 1),
            new Badge("stars_50", "🌟", "חמישים כוכבים", "stars", 50),
            new Badge("stars_200", "✨", "מאתיים כוכבים", "stars", 200),
            new Badge("correct_50", "✅", "חמישים נכונות", "correct", 50),
            new Badge("correct_200", "💯", "מאתיים נכונות", "correct", 200),
            new Badge("correct_1000", "🏅", "אלף נכונות", "correct", 1000),
            new Badge("streak_3", "🔥", "שלושה ימים ברצף", "streak", 3),
            new Badge("streak_7", "🔥", "שבוע ברצף", "streak", 7),
            new Badge("streak_30", "🏆", "חודש ברצף", "streak", 30),
            new Badge("stage_1", "🏝️", "השלב הראשון", "stages", 1),
            new Badge("stage_12", "🗺️", "שנים־עשר שלבים", "stages", 12),
            new Badge("stage_40", "👑", "ארבעים שלבים", "stages", 40),
            new Badge("builder_5", "🏗️", "בנאי האי", "buildings", 5),
            new Badge("builder_20", "🏰", "האי הגדול", "buildings", 20),
            new Badge("award_1", "🎁", "פרס מהמורה", "awards", 1));

    public record Counts(int stars, long correct, int streak, int stages, int buildings, long awards) {
        int of(String signal) {
            return switch (signal) {
                case "stars" -> stars;
                case "correct" -> (int) Math.min(correct, Integer.MAX_VALUE);
                case "streak" -> streak;
                case "stages" -> stages;
                case "buildings" -> buildings;
                case "awards" -> (int) Math.min(awards, Integer.MAX_VALUE);
                default -> 0;
            };
        }
    }

    /** Every badge with earned/new/progress. seenKeys = badges the child already saw (so "new" shows once). */
    public static List<Map<String, Object>> badges(Counts c, java.util.Set<String> seenKeys) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Badge b : ALL) {
            int have = c.of(b.signal());
            boolean earned = have >= b.need();
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("key", b.key());
            m.put("emoji", b.emoji());
            m.put("name", b.name());
            m.put("earned", earned);
            m.put("isNew", earned && !seenKeys.contains(b.key()));
            m.put("have", Math.min(have, b.need()));
            m.put("need", b.need());
            out.add(m);
        }
        return out;
    }

    /** The keys the child has earned right now. */
    public static List<String> earnedKeys(Counts c) {
        List<String> keys = new ArrayList<>();
        for (Badge b : ALL) {
            if (c.of(b.signal()) >= b.need()) keys.add(b.key());
        }
        return keys;
    }
}
