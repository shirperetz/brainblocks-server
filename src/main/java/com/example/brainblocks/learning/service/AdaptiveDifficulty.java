package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;

import java.util.Comparator;
import java.util.List;

/**
 * 2026-11-12 (idea #3 - "difficulty that adjusts itself"): the numbers grow with the child. From the
 * last {@link #RECENT} answers in the SAME topic (not the 60-second race): at least {@link #MIN}
 * answers and >= {@link #UP_PCT}% right -> one level harder than the profile's difficulty;
 * <= {@link #DOWN_PCT}% right -> one level easier (smaller numbers); otherwise as set.
 * In code, no AI - the same rule every time, and the teacher's report still shows what is hard.
 */
public final class AdaptiveDifficulty {

    private AdaptiveDifficulty() {}

    static final int RECENT = 20;
    static final int MIN = 10;
    static final int UP_PCT = 90;
    static final int DOWN_PCT = 50;

    public static Difficulty forTopic(Difficulty base, Topic topic, List<LearnerAnswer> answers) {
        Difficulty d = base == null ? Difficulty.MEDIUM : base;
        List<LearnerAnswer> recent = answers.stream()
                .filter(a -> a.getTopic() == topic && a.getMode() != SessionMode.TIMED)
                .sorted(Comparator.comparing(LearnerAnswer::getCreatedAt).reversed())
                .limit(RECENT)
                .toList();
        if (recent.size() < MIN) return d;
        long right = recent.stream().filter(LearnerAnswer::isCorrect).count();
        int pct = (int) Math.round(right * 100.0 / recent.size());
        if (pct >= UP_PCT) return harder(d);
        if (pct <= DOWN_PCT) return easier(d);
        return d;
    }

    static Difficulty harder(Difficulty d) {
        return d == Difficulty.EASY ? Difficulty.MEDIUM : Difficulty.HARD;
    }

    static Difficulty easier(Difficulty d) {
        return d == Difficulty.HARD ? Difficulty.MEDIUM : Difficulty.EASY;
    }
}
