package com.example.brainblocks.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * No wrong question may reach a child: for every topic x grade x difficulty x stage, many samples -
 * the marked answer is the real answer, 4 distinct non-negative options, and grade א stays within 20.
 */
class LearningQuestionServiceTest {

    private static final Pattern ARITH = Pattern.compile("כמה זה (\\d+) ([+\\-×÷]) (\\d+)\\?");
    private static final Pattern SEQ = Pattern.compile("(\\d+), (\\d+), (\\d+), (\\d+), \\?");
    private static final Topic[] TOPICS = {Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION,
            Topic.DIVISION, Topic.COMPARISON, Topic.SEQUENCES, Topic.WORD_PROBLEMS, Topic.MIXED};

    private final LearningQuestionService service = new LearningQuestionService();

    @Test
    void everyQuestionIsCorrectAndWellFormed() {
        int checked = 0;
        for (Topic topic : TOPICS) {
            for (Grade grade : Grade.values()) {
                for (Difficulty diff : Difficulty.values()) {
                    for (int stage = 1; stage <= 6; stage++) {
                        for (QuestionResponse q : service.generate(topic, grade, diff, stage, 20)) {
                            String where = topic + "/" + grade + "/" + diff + "/" + stage + ": " + q.getText() + " " + q.getOptions();
                            List<String> opts = q.getOptions();
                            assertEquals(4, opts.size(), where);
                            assertEquals(4, new HashSet<>(opts).size(), "options must differ - " + where);
                            assertTrue(q.getCorrectIndex() >= 0 && q.getCorrectIndex() < 4, where);
                            for (String o : opts) assertTrue(Long.parseLong(o) >= 0, "no negative options - " + where);
                            long marked = Long.parseLong(opts.get(q.getCorrectIndex()));
                            Long expected = expectedAnswer(q, opts);
                            if (expected != null) assertEquals(expected.longValue(), marked, where);
                            if (grade == Grade.G1 && q.getTopic() == Topic.ADDITION) {
                                assertTrue(marked <= 20, "grade א addition stays within 20 - " + where);
                            }
                            assertFalse(q.getHint() == null || q.getHint().isBlank(), where);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 5_000);
    }

    @Test
    void wordProblemsUseTheRightGenderForTheName() {
        for (int i = 0; i < 400; i++) {
            String t = service.generate(Topic.WORD_PROBLEMS, Grade.G4, Difficulty.MEDIUM, 3, 1).get(0).getText();
            for (String girl : new String[] {"מאיה", "נועה", "שירה", "תמר"}) {
                if (t.startsWith("ל" + girl)) assertFalse(t.contains("וקיבל עוד"), t);
            }
            for (String boy : new String[] {"דני", "יוסי", "איתי", "עומר"}) {
                if (t.startsWith("ל" + boy)) assertFalse(t.contains("וקיבלה עוד"), t);
            }
        }
    }

    /** The real answer, computed from the question text (null when the text isn't a formula). */
    private static Long expectedAnswer(QuestionResponse q, List<String> opts) {
        String t = q.getText();
        Matcher m = ARITH.matcher(t);
        if (m.find()) {
            long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(3));
            return switch (m.group(2)) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "×" -> a * b;
                default -> {
                    assertEquals(0, a % b, "division must come out whole - " + t);
                    yield a / b;
                }
            };
        }
        Matcher s = SEQ.matcher(t);
        if (s.find()) {
            long t3 = Long.parseLong(s.group(3)), t4 = Long.parseLong(s.group(4));
            return t4 + (t4 - t3);
        }
        if (q.getTopic() == Topic.COMPARISON) {
            return opts.stream().mapToLong(Long::parseLong).max().orElseThrow();
        }
        // Word problems: check the worked solution in the explanation, and that it matches the story.
        Matcher w = Pattern.compile("(\\d+) (ועוד|פחות|כפול) (\\d+) זה (\\d+)").matcher(q.getExplanation());
        if (w.find()) {
            long a = Long.parseLong(w.group(1)), b = Long.parseLong(w.group(3));
            long r = switch (w.group(2)) { case "ועוד" -> a + b; case "פחות" -> a - b; default -> a * b; };
            assertEquals(r, Long.parseLong(w.group(4)), "explanation math - " + q.getExplanation());
            assertTrue(t.contains(String.valueOf(a)) && t.contains(String.valueOf(b)), "story has the numbers - " + t);
            return r;
        }
        return null;
    }
}
