package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-11: the progress screen says "hard: adding with a carry" - so every question of every topic,
 * grade and stage must carry a real skill (never "OTHER"), and the text-recognised ones must be right.
 */
class SkillsTest {

    private static final Set<Topic> ISLAND = EnumSet.of(Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN);

    @Test
    void everyGeneratedQuestionHasAKnownSkill() {
        LearningQuestionService gen = new LearningQuestionService();
        Set<String> seen = new HashSet<>();
        for (Topic topic : Topic.values()) {
            if (ISLAND.contains(topic)) continue; // built in the client; recognised by stage (below)
            for (Grade grade : Grade.values()) {
                for (int stage = 1; stage <= 6; stage++) {
                    for (int round = 0; round < 15; round++) {
                        for (QuestionResponse q : gen.generate(topic, grade, Difficulty.MEDIUM, stage, 10)) {
                            assertThat(Skills.known(q.getSkill())).as(topic + " " + q.getText()).isTrue();
                            assertThat(q.getSkill()).as(topic + " " + q.getText()).isNotEqualTo("OTHER");
                            seen.add(q.getSkill());
                        }
                    }
                }
            }
        }
        // The common ones really show up (not everything collapsed into one tag).
        assertThat(seen).contains("ADD_CARRY", "ADD_NO_CARRY", "SUB_BORROW", "MUL_TABLE", "MISSING_ADD",
                "CMP_SMALLEST", "SEQ_DOWN", "WP_SUB", "FRAC_SIMPLIFY", "ANG_TRIANGLE", "AREA_RECTANGLE");
    }

    @Test
    void arithmeticIsSortedByWhatMakesItHard() {
        assertThat(Skills.of(null, Topic.ADDITION, 1, "כמה זה 3 + 4?")).isEqualTo("ADD_TO_10");
        assertThat(Skills.of(null, Topic.ADDITION, 3, "כמה זה 38 + 27?")).isEqualTo("ADD_CARRY");
        assertThat(Skills.of(null, Topic.ADDITION, 3, "כמה זה 32 + 25?")).isEqualTo("ADD_NO_CARRY");
        assertThat(Skills.of(null, Topic.SUBTRACTION, 3, "כמה זה 42 - 17?")).isEqualTo("SUB_BORROW");
        assertThat(Skills.of(null, Topic.SUBTRACTION, 3, "כמה זה 48 - 17?")).isEqualTo("SUB_NO_BORROW");
        assertThat(Skills.of(null, Topic.MULTIPLICATION, 3, "כמה זה 7 × 8?")).isEqualTo("MUL_TABLE");
        assertThat(Skills.of(null, Topic.DIVISION, 3, "כמה זה 56 ÷ 8?")).isEqualTo("DIV_TABLE");
        assertThat(Skills.of(null, Topic.ADDITION, 3, "מה המספר החסר? 7 + ? = 12")).isEqualTo("MISSING_ADD");
        assertThat(Skills.of(null, Topic.SEQUENCES, 3, "מה המספר הבא? 20, 17, 14, 11, ?")).isEqualTo("SEQ_DOWN");
    }

    @Test
    void islandGamesBySkillOfTheLevel() {
        assertThat(Skills.of(null, Topic.DRAGON, 4, "")).isEqualTo("ISL_COUNT_ON");
        assertThat(Skills.of(null, Topic.BALLOONS, 6, "")).isEqualTo("ISL_CROSS_10");
        assertThat(Skills.of(null, Topic.BRIDGE, 2, "")).isEqualTo("ISL_MAKE_10");
    }

    @Test
    void aSentTagIsKeptAndAnUnknownOneIsReplaced() {
        assertThat(Skills.of("FRAC_SIMPLIFY", Topic.FRACTIONS, 6, "x")).isEqualTo("FRAC_SIMPLIFY");
        assertThat(Skills.of("<script>", Topic.ADDITION, 1, "כמה זה 3 + 4?")).isEqualTo("ADD_TO_10");
    }
}
