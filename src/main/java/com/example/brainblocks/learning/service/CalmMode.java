package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.LearnerAnswer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2026-11-21 (Shir chose "calm mode - so a struggling child doesn't give up and leave"): two options
 * instead of four while the child is finding its feet in a topic, four again once it is going well
 * (OK_PCT of the last LAST answers). The other calm parts (5 questions, no typed answers, unlimited
 * help, soft words) live in the app; the options are cut HERE, so the bank row - which the server
 * judges by - holds exactly what the child saw.
 */
public final class CalmMode {

    private CalmMode() {}

    static final int LAST = 10;
    static final int OK_PCT = 80;

    /** True = this topic is still hard for her -> two options. */
    public static boolean twoOptions(List<LearnerAnswer> topicHistoryNewestFirst) {
        List<LearnerAnswer> last = new ArrayList<>();
        for (LearnerAnswer a : topicHistoryNewestFirst) {
            if (Foundations.isBaseSkill(a.getSkill())) continue;
            last.add(a);
            if (last.size() >= LAST) break;
        }
        if (last.size() < LAST) return true;
        long right = last.stream().filter(LearnerAnswer::isCorrect).count();
        return right * 100 < OK_PCT * (long) last.size();
    }

    /**
     * The right answer + ONE other - the closest number when the options are numbers (a real
     * choice, not a giveaway), a random one otherwise. Order random.
     */
    public static QuestionResponse twoOf(QuestionResponse q, Random random) {
        List<String> opts = q.getOptions();
        int ci = q.getCorrectIndex();
        if (opts == null || opts.size() <= 2 || ci < 0 || ci >= opts.size()) return q;
        String right = opts.get(ci);
        String other = null;
        java.math.BigDecimal r = LearnQuestionNumbers.number(right);
        if (r != null) {
            java.math.BigDecimal best = null;
            for (int i = 0; i < opts.size(); i++) {
                if (i == ci) continue;
                java.math.BigDecimal v = LearnQuestionNumbers.number(opts.get(i));
                if (v == null) { best = null; other = null; break; }
                java.math.BigDecimal d = v.subtract(r).abs();
                if (best == null || d.compareTo(best) < 0) { best = d; other = opts.get(i); }
            }
        }
        if (other == null) {
            List<String> rest = new ArrayList<>(opts);
            rest.remove(ci);
            other = rest.get(random.nextInt(rest.size()));
        }
        boolean rightFirst = random.nextBoolean();
        List<String> two = rightFirst ? List.of(right, other) : List.of(other, right);
        return new QuestionResponse(q.getId(), q.getTopic(), q.getText(), two, rightFirst ? 0 : 1,
                q.getHint(), q.getExplanation(), q.getVisual()).withSkill(q.getSkill());
    }

    /** The plain-number reader LearnQuestion uses ("1,250" = 1250). */
    static final class LearnQuestionNumbers {
        private LearnQuestionNumbers() {}

        static java.math.BigDecimal number(String s) {
            if (s == null) return null;
            String t = s.trim().replace('−', '-').replace(",", "").replace(" ", "").replace(String.valueOf((char) 0xA0), "");
            if (!t.matches("-?[0-9]+([.][0-9]+)?")) return null;
            return new java.math.BigDecimal(t);
        }
    }
}
