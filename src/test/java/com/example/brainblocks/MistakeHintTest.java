package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.ai.MistakeHint;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 2026-11-13: the free help - a sentence for the child's own mistake, and a drawing - never the answer. */
class MistakeHintTest {

    @Test
    void aSentenceForTheMistake() {
        // 38 + 25 = 63; 53 = forgot the carry
        assertThat(MistakeHint.forMistake("כמה זה 38 + 25?", "63", "53")).contains("העשרת");
        // 7 × 8 = 56; 48 = the neighbour row
        assertThat(MistakeHint.forMistake("כמה זה 7 × 8?", "56", "48")).isNotNull();
        // right, or nothing chosen: no sentence
        assertThat(MistakeHint.forMistake("כמה זה 38 + 25?", "63", "63")).isNull();
        assertThat(MistakeHint.forMistake("כמה זה 38 + 25?", "63", null)).isNull();
        // the sentences never contain a number that could be an answer
        for (String[] q : List.of(new String[] {"כמה זה 38 + 25?", "63", "53"}, new String[] {"כמה זה 7 × 8?", "56", "48"},
                new String[] {"כמה זה 15 - 7?", "8", "9"})) {
            String s = MistakeHint.forMistake(q[0], q[1], q[2]);
            if (s != null) assertThat(s).doesNotContain(q[1]);
        }
    }

    @Test
    void aDrawingOfTheQuestionNotTheAnswer() {
        assertThat(MistakeHint.visual("כמה זה 7 + 5?")).containsEntry("kind", "line").containsEntry("start", 7).containsEntry("jump", 5);
        assertThat(MistakeHint.visual("כמה זה 47 + 38?")).containsEntry("kind", "blocks").containsEntry("numbers", List.of(47, 38));
        assertThat(MistakeHint.visual("כמה זה 4 × 6?")).containsEntry("kind", "groups").containsEntry("groups", 4).containsEntry("each", 6);
        assertThat(MistakeHint.visual("כמה זה 12 : 3?")).containsEntry("kind", "share").containsEntry("total", 12);
        assertThat(MistakeHint.visual("איזה חלק צבוע? 3/8")).containsEntry("kind", "fraction").containsEntry("whole", 8);
        assertThat(MistakeHint.visual("כמה צלעות יש למשושה?")).isNull();
        // never the result: 7 + 5 -> no 12 anywhere in the drawing
        Map<String, Object> v = MistakeHint.visual("כמה זה 7 + 5?");
        assertThat(v.values()).doesNotContain(12);
    }
}
