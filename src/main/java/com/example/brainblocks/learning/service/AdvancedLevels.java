package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 2026-11-21 (Shir, overnight): "when the child is really good the exercise gets harder - but it does
 * not move to a higher grade's level without the teacher's approval; a feature for the admin and the
 * teacher". Harder-in-grade is AdaptiveDifficulty (HARD, capped at the grade's ceiling). This is the
 * other half: per topic, the child's teacher or the admin may open a higher grade's material for
 * one child ("⭐ ready for a challenge" suggests it); the server then generates that topic at the
 * approved grade. Nothing changes until a person approves.
 */
public final class AdvancedLevels {

    private AdvancedLevels() {}

    /** Answers looked at, how many at least, and the share that counts as "really good". */
    static final int RECENT = 20;
    static final int MIN = 20;
    static final int READY_PCT = 90;

    /** The grade a topic is generated at for this child: the approved higher grade, else her own. */
    public static Grade effective(LearnerProfile p, Topic t) {
        Grade own = p.getGrade();
        Grade adv = p.getAdvancedLevels().get(t);
        if (adv == null) return own;
        if (own == null || adv.ordinal() > own.ordinal()) return adv;
        return own;
    }

    /** Topics whose recent answers are >= 90% right (at least 20) - the "ready for a challenge" signal. */
    public static List<Map<String, Object>> ready(LearnerProfile p, List<LearnerAnswer> answers) {
        Map<Topic, List<LearnerAnswer>> byTopic = new LinkedHashMap<>();
        answers.stream()
                .filter(a -> a.getTopic() != null && !a.getTopic().isGame() && a.getMode() != SessionMode.TIMED)
                .sorted(Comparator.comparing(LearnerAnswer::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .forEach(a -> byTopic.computeIfAbsent(a.getTopic(), k -> new ArrayList<>()).add(a));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : byTopic.entrySet()) {
            List<LearnerAnswer> recent = e.getValue().subList(0, Math.min(RECENT, e.getValue().size()));
            if (recent.size() < MIN || e.getKey() == Topic.MIXED) continue;
            long right = recent.stream().filter(LearnerAnswer::isCorrect).count();
            int pct = (int) Math.round(right * 100.0 / recent.size());
            Grade now = effective(p, e.getKey());
            if (pct < READY_PCT || now == null || now == Grade.G6) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("topic", e.getKey().name());
            m.put("pct", pct);
            m.put("answers", recent.size());
            m.put("nextGrade", Grade.values()[now.ordinal() + 1].name());
            out.add(m);
        }
        return out;
    }
}
