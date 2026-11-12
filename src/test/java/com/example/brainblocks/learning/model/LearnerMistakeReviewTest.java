package com.example.brainblocks.learning.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 2026-11-12 (idea #4): a mistake comes back after a day, 3 days and a week before it counts as learned. */
class LearnerMistakeReviewTest {

    private final LocalDateTime t0 = LocalDateTime.of(2026, 11, 12, 17, 0);

    private LearnerMistake fresh() {
        return new LearnerMistake(1L, Topic.ADDITION, "כמה זה 38 + 27?", "55", "65", "");
    }

    @Test
    void aDayThreeDaysAWeekThenLearned() {
        LearnerMistake m = fresh();
        assertThat(m.isDue(t0)).as("a new mistake is due now").isTrue();

        m.reviewedRight(t0);
        assertThat(m.isResolved()).isFalse();
        assertThat(m.isDue(t0.plusHours(23))).isFalse();
        assertThat(m.isDue(t0.plusDays(1))).isTrue();

        m.reviewedRight(t0.plusDays(1));
        assertThat(m.isDue(t0.plusDays(3))).isFalse();
        assertThat(m.isDue(t0.plusDays(4))).isTrue();

        m.reviewedRight(t0.plusDays(4));
        assertThat(m.isDue(t0.plusDays(10))).isFalse();
        assertThat(m.isDue(t0.plusDays(11))).isTrue();

        m.reviewedRight(t0.plusDays(11));
        assertThat(m.isResolved()).as("right 4 times over 11 days -> learned").isTrue();
        assertThat(m.isDue(t0.plusDays(100))).isFalse();
    }

    @Test
    void wrongInTheReviewComesBackTomorrowFromTheStart() {
        LearnerMistake m = fresh();
        m.reviewedRight(t0);
        m.reviewedRight(t0.plusDays(1));
        m.reviewedWrong(t0.plusDays(4));
        assertThat(m.getReviewStep()).isZero();
        assertThat(m.isDue(t0.plusDays(4).plusHours(12))).isFalse();
        assertThat(m.isDue(t0.plusDays(5))).isTrue();
        m.reviewedRight(t0.plusDays(5));
        assertThat(m.getNextReviewAt()).isEqualTo(t0.plusDays(6)); // back to the 1-day gap
    }
}
