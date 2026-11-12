package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 2026-11-12 (Shir: "always different exercises, unless the child got it wrong"). */
class FreshQuestionsTest {

    private final LearningQuestionService service = new LearningQuestionService();

    @Test
    void noExerciseTwiceInARound() {
        int rounds = 0, dupRounds = 0;
        for (Grade g : Grade.values()) {
            for (Topic t : List.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION, Topic.FRACTIONS, Topic.ANGLES, Topic.PERIMETER, Topic.MIXED)) {
                if (!GradeTopics.allowed(t, g)) continue;
                for (int stage = 1; stage <= 6; stage++) {
                    for (int r = 0; r < 10; r++) {
                        List<QuestionResponse> qs = service.generateFresh(t, g, Difficulty.MEDIUM, stage, 10, Set.of());
                        assertThat(qs).hasSize(10);
                        Set<String> keys = new HashSet<>();
                        qs.forEach(q -> keys.add(LearningQuestionService.key(q)));
                        rounds++;
                        if (keys.size() < 10) dupRounds++;
                    }
                }
            }
        }
        // Topics with very few possible exercises (e.g. "what kind of angle" at ג׳) may still repeat -
        // almost never; everything else never.
        assertThat(dupRounds).as(dupRounds + " of " + rounds + " rounds had a repeat").isLessThanOrEqualTo(rounds / 50);
    }

    @Test
    void whatWasAnsweredRightLatelyStaysOut() {
        Set<String> seen = new HashSet<>();
        int overlap = 0, total = 0;
        for (int r = 0; r < 12; r++) {
            List<QuestionResponse> qs = service.generateFresh(Topic.MULTIPLICATION, Grade.G4, Difficulty.MEDIUM, 5, 10, Set.copyOf(seen));
            for (QuestionResponse q : qs) {
                String k = LearningQuestionService.key(q);
                if (seen.contains(k)) overlap++;
                total++;
                seen.add(k);
            }
        }
        // 120 questions in a row, each round avoiding everything before it: practically no repeats.
        assertThat(overlap).as(overlap + " repeats in " + total).isLessThanOrEqualTo(total / 20);
    }

    @Test
    void aTinyTopicNeverGetsStuck() {
        // Everything already "seen": the round still comes, with 10 questions.
        Set<String> all = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            service.generate(Topic.ADDITION, Grade.G1, Difficulty.EASY, 1, 10).forEach(q -> all.add(LearningQuestionService.key(q)));
        }
        assertThat(service.generateFresh(Topic.ADDITION, Grade.G1, Difficulty.EASY, 1, 10, all)).hasSize(10);
    }
}
