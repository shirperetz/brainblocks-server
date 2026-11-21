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
     * The right answer + ONE other. With numbers: not the closest (8239 vs 8238 is a trap for a child
     * who struggles - found in the demo screenshots) and not the farthest (a giveaway) - the middle
     * one of the wrong options. Otherwise a random one. Order random.
     */
    public static QuestionResponse twoOf(QuestionResponse q, Random random) {
        List<String> opts = q.getOptions();
        int ci = q.getCorrectIndex();
        if (opts == null || opts.size() <= 2 || ci < 0 || ci >= opts.size()) return q;
        String right = opts.get(ci);
        String other = null;
        java.math.BigDecimal r = LearnQuestionNumbers.number(right);
        if (r != null) {
            List<String> wrong = new ArrayList<>();
            boolean allNumbers = true;
            for (int i = 0; i < opts.size(); i++) {
                if (i == ci) continue;
                if (LearnQuestionNumbers.number(opts.get(i)) == null) { allNumbers = false; break; }
                wrong.add(opts.get(i));
            }
            if (allNumbers && !wrong.isEmpty()) {
                wrong.sort(java.util.Comparator.comparing(o -> LearnQuestionNumbers.number(o).subtract(r).abs()));
                other = wrong.get(wrong.size() >= 3 ? 1 : wrong.size() - 1);
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
