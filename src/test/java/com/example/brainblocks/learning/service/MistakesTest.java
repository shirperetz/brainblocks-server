package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2026-11-12, stage 2/B: where a wrong answer comes from. */
class MistakesTest {

    private static final Set<Topic> ISLAND = EnumSet.of(Topic.DRAGON, Topic.BALLOONS, Topic.BRIDGE, Topic.TRAIN,
            Topic.CLOCK, Topic.SHOP, Topic.COINS, Topic.MIXED);

    @Test
    void knownMistakes() {
        assertEquals(Mistakes.CARRY, Mistakes.classify(Topic.ADDITION, "כמה זה 47 + 38?", "85", "75"));
        assertEquals(Mistakes.CARRY, Mistakes.classify(Topic.SUBTRACTION, "כמה זה 52 - 17?", "35", "45")); // 7-2 in the ones
        assertEquals(Mistakes.PLACE_VALUE, Mistakes.classify(Topic.ADDITION, "כמה זה 47 + 38?", "85", "105"));
        assertEquals(Mistakes.COUNTING, Mistakes.classify(Topic.ADDITION, "כמה זה 4 + 3?", "7", "8"));
        assertEquals(Mistakes.WRONG_OPERATION, Mistakes.classify(Topic.SUBTRACTION, "כמה זה 9 - 4?", "5", "13"));
        assertEquals(Mistakes.TIMES_TABLE, Mistakes.classify(Topic.MULTIPLICATION, "כמה זה 7 × 8?", "56", "63"));
        assertEquals(Mistakes.WRONG_OPERATION, Mistakes.classify(Topic.MULTIPLICATION, "כמה זה 7 × 8?", "56", "15"));
        assertEquals(Mistakes.WRONG_OPERATION, Mistakes.classify(Topic.ADDITION, "מה המספר החסר? ? + 5 = 12", "7", "17"));
        assertEquals(Mistakes.SEQUENCE_STEP, Mistakes.classify(Topic.SEQUENCES, "מה המספר הבא? 3, 6, 9, 12, ?", "15", "18"));
        assertEquals(Mistakes.WRONG_OPERATION, Mistakes.classify(Topic.WORD_PROBLEMS,
                "לדני היו 12 תפוחים. 5 תפוחים הלכו לאיבוד. כמה נשארו?", "7", "17"));
        assertEquals(Mistakes.ADD_DENOMINATORS, Mistakes.classify(Topic.FRACTIONS, "2/7 + 3/7 = ?", "5/7", "5/14"));
        assertEquals(Mistakes.NO_COMMON_DENOMINATOR, Mistakes.classify(Topic.FRACTIONS, "1/2 + 1/4 = ?", "3/4", "2/4"));
        assertEquals(Mistakes.REVERSED, Mistakes.classify(Topic.FRACTIONS, "כתבו את התרגיל 2 : 3 כשבר", "2/3", "3/2"));
        assertEquals(Mistakes.INCOMPLETE, Mistakes.classify(Topic.FRACTIONS, "צמצמו עד הסוף את 8/12", "2/3", "4/6"));
        assertEquals(Mistakes.PART_WHOLE, Mistakes.classify(Topic.FRACTIONS, "איזה חלק מהעיגול צבוע?", "3/5", "2/5"));
        assertEquals(Mistakes.AREA_PERIMETER, Mistakes.classify(Topic.AREA,
                "מה השטח של מלבן שאורכו 6 ס\"מ ורוחבו 4 ס\"מ? (בסמ\"ר)", "24", "20"));
        assertEquals(Mistakes.AREA_PERIMETER, Mistakes.classify(Topic.PERIMETER,
                "מה ההיקף של מלבן שאורכו 6 ס\"מ ורוחבו 4 ס\"מ?", "20", "24"));
        assertEquals(Mistakes.WRONG_MEASURE, Mistakes.classify(Topic.AREA,
                "למקבילית בסיס 8 ס\"מ, צלע משופעת 6 ס\"מ וגובה 5 ס\"מ. מה השטח? (בסמ\"ר)", "40", "48"));
        assertEquals(Mistakes.ANGLE_SUM, Mistakes.classify(Topic.ANGLE_CALC,
                "במשולש יש זווית של 50° וזווית של 60°. כמה מעלות הזווית השלישית?", "70", "250"));
        // right / empty answers are not mistakes; nonsense is UNKNOWN, never an exception
        assertNull(Mistakes.classify(Topic.ADDITION, "כמה זה 4 + 3?", "7", "7"));
        assertNull(Mistakes.classify(Topic.ADDITION, "כמה זה 4 + 3?", "7", null));
        assertEquals(Mistakes.UNKNOWN, Mistakes.classify(Topic.DRAGON, "🐉 ???", "abc", "xyz"));
    }

    /**
     * Thousands of real generated questions, every topic / grade / stage: each wrong option gets a label
     * (never null, never an exception), the right answer never does, and most wrong options in every
     * topic are explained (not UNKNOWN).
     */
    @Test
    void everyGeneratedWrongOptionIsLabelled() throws Exception {
        LearningQuestionService gen = new LearningQuestionService();
        Map<Topic, int[]> byTopic = new TreeMap<>();
        Map<String, Integer> byType = new TreeMap<>();
        for (Topic t : Topic.values()) {
            if (ISLAND.contains(t)) continue;
            for (Grade g : Grade.values()) {
                for (int stage = 1; stage <= 6; stage++) {
                    for (QuestionResponse q : gen.generate(t, g, Difficulty.MEDIUM, stage, 40)) {
                        String right = q.getOptions().get(q.getCorrectIndex());
                        assertNull(Mistakes.classify(q.getTopic(), q.getText(), right, right));
                        for (String o : q.getOptions()) {
                            if (o.equals(right)) continue;
                            String type = Mistakes.classify(q.getTopic(), q.getText(), right, o);
                            assertNotNull(type, q.getText() + " / " + o);
                            int[] c = byTopic.computeIfAbsent(q.getTopic(), k -> new int[2]);
                            c[0]++;
                            if (!Mistakes.UNKNOWN.equals(type)) c[1]++;
                            byType.merge(type, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        StringBuilder report = new StringBuilder();
        byTopic.forEach((t, c) -> report.append(String.format("%-14s %5d wrong options, %3d%% explained%n", t, c[0], c[1] * 100 / c[0])));
        byType.forEach((k, v) -> report.append(String.format("  %-22s %d%n", k, v)));
        System.out.println(report);
        byTopic.forEach((t, c) -> assertTrue(c[1] * 100 / c[0] >= 70, t + ": only " + c[1] * 100 / c[0] + "% explained\n" + report));
    }

    /** The labels stay on the server: the question sent to the phone has no trace of them. */
    @Test
    void nothingNewReachesThePhone() throws Exception {
        LearningQuestionService gen = new LearningQuestionService();
        String json = new ObjectMapper().writeValueAsString(gen.generate(Topic.ADDITION, Grade.G3, Difficulty.MEDIUM, 3, 5)
                .stream().map(QuestionResponse::withoutAnswer).toList());
        for (String code : List.of("\"mistake", "errorType", "PLACE_VALUE", "WRONG_OPERATION")) assertFalse(json.contains(code), json);
    }

    @Test
    void aPatternNeedsThreeOfTheSameKind() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 12, 0);
        List<LearnerAnswer> answers = new ArrayList<>();
        answers.add(wrong("כמה זה 47 + 38?", "85", "75", now));
        answers.add(wrong("כמה זה 26 + 18?", "44", "34", now));
        answers.add(right("כמה זה 4 + 3?", "7", now));
        assertNull(ProgressReport.pattern(answers), "two are not a pattern yet");

        answers.add(wrong("כמה זה 39 + 45?", "84", "74", now));
        Map<String, Object> p = ProgressReport.pattern(answers);
        assertNotNull(p);
        assertEquals(Mistakes.CARRY, p.get("type"));
        assertEquals(3, p.get("count"));
        assertFalse(((String) p.get("label")).isBlank());

        // UNKNOWN mistakes never make a pattern
        List<LearnerAnswer> odd = new ArrayList<>();
        for (int i = 0; i < 5; i++) odd.add(wrong("כמה זה 47 + 38?", "85", "50", now));
        assertNull(ProgressReport.pattern(odd));
    }

    private static LearnerAnswer wrong(String text, String correct, String given, LocalDateTime at) {
        return new LearnerAnswer(1L, Topic.ADDITION, "ADD_CARRY", 3, SessionMode.STAGE, false, false, text, given, correct).at(at);
    }

    private static LearnerAnswer right(String text, String correct, LocalDateTime at) {
        return new LearnerAnswer(1L, Topic.ADDITION, "ADD_CARRY", 3, SessionMode.STAGE, true, false, text, correct, correct).at(at);
    }
}
