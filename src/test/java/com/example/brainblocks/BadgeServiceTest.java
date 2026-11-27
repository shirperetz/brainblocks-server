package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.service.BadgeService;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 2026-11-27 (idea #6): badges are earned from the child's own counts; "new" shows once. */
class BadgeServiceTest {

    @Test
    void earnedByReachingTheNeededCount() {
        var none = new BadgeService.Counts(0, 0, 0, 0, 0, 0);
        assertThat(BadgeService.earnedKeys(none)).isEmpty();

        var some = new BadgeService.Counts(60, 250, 7, 13, 6, 1);
        var keys = BadgeService.earnedKeys(some);
        assertThat(keys).contains("first_star", "stars_50", "correct_50", "correct_200",
                "streak_3", "streak_7", "stage_1", "stage_12", "builder_5", "award_1");
        assertThat(keys).doesNotContain("stars_200", "correct_1000", "streak_30", "stage_40", "builder_20");

        // progress is capped at need; a locked badge shows how far
        var one = BadgeService.badges(some, Set.of()).stream().filter(b -> b.get("key").equals("stars_200")).findFirst().orElseThrow();
        assertThat(one.get("earned")).isEqualTo(false);
        assertThat(one.get("have")).isEqualTo(60);
        assertThat(one.get("need")).isEqualTo(200);

        // "new" = earned and not yet seen
        var withSeen = BadgeService.badges(some, Set.of("first_star"));
        var first = withSeen.stream().filter(b -> b.get("key").equals("first_star")).findFirst().orElseThrow();
        var stars50 = withSeen.stream().filter(b -> b.get("key").equals("stars_50")).findFirst().orElseThrow();
        assertThat(first.get("isNew")).isEqualTo(false);
        assertThat(stars50.get("isNew")).isEqualTo(true);
    }
}
