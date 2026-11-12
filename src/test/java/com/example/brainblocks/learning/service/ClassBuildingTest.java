package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 2026-11-12 (idea #6): the class builds one building a week from everyone's right answers. */
class ClassBuildingTest {

    @Test
    void theBuildingGrowsWithTheWholeClass() {
        // 4 children -> 120 right answers to finish.
        Map<String, Object> empty = ClassBuilding(4, List.of(0L, 0L, 0L, 0L));
        assertThat(empty).containsEntry("target", 120).containsEntry("built", 0L).containsEntry("stage", 0)
                .containsEntry("helped", 0).containsEntry("done", false);

        Map<String, Object> half = ClassBuilding(4, List.of(30L, 30L, 0L, 0L));
        assertThat(half).containsEntry("built", 60L).containsEntry("stage", 2).containsEntry("helped", 2);

        Map<String, Object> full = ClassBuilding(4, List.of(30L, 30L, 30L, 30L));
        assertThat(full).containsEntry("built", 120L).containsEntry("stage", 5).containsEntry("done", true);
    }

    @Test
    void oneChildCannotBuildItAlone() {
        // 500 right answers of one child count as 60 (twice its share) - the others are needed.
        Map<String, Object> m = ClassBuilding(4, List.of(500L, 0L, 0L, 0L));
        assertThat(m).containsEntry("built", 60L).containsEntry("stage", 2).containsEntry("done", false);
        // Never more than the target.
        assertThat(ClassBuilding(2, List.of(500L, 500L))).containsEntry("built", 60L).containsEntry("stage", 5);
        // An empty class still has a goal (no division by zero).
        assertThat(ClassBuilding(0, List.of())).containsEntry("target", 30).containsEntry("stage", 0);
    }

    @Test
    void theWeekStartsOnSunday() {
        assertThat(ClassBuildingService.weekStart(LocalDate.of(2026, 11, 12))) // Thursday
                .isEqualTo(LocalDate.of(2026, 11, 8).atStartOfDay());
        assertThat(ClassBuildingService.weekStart(LocalDate.of(2026, 11, 8))) // Sunday itself
                .isEqualTo(LocalDate.of(2026, 11, 8).atStartOfDay());
        assertThat(ClassBuildingService.weekStart(LocalDate.of(2026, 11, 14))) // Saturday
                .isEqualTo(LocalDate.of(2026, 11, 8).atStartOfDay());
    }

    private static Map<String, Object> ClassBuilding(int children, List<Long> right) {
        return ClassBuildingService.picture(children, right);
    }
}
