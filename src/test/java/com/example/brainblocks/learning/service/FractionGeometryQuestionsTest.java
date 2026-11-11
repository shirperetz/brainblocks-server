package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.ai.Tutor;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Grade;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 2026-11-11: every fraction / geometry question is solved again here, independently, from its own
 * text - the marked answer must be right, and no wrong option may be equal to it (e.g. 2/4 vs 1/2).
 */
class FractionGeometryQuestionsTest {

    private static final Pattern FRAC = Pattern.compile("(\\d+)/(\\d+)");
    private static final Pattern NUM = Pattern.compile("\\d+");

    @Test
    void fractionsAreAlwaysRight() {
        for (long seed = 0; seed < 400; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (Grade g : List.of(Grade.G5, Grade.G6)) {
                for (int stage = 1; stage <= 6; stage++) {
                    QuestionResponse q = gen.fraction(0, g, stage, true);
                    checkShape(q);
                    String correct = q.getOptions().get(q.getCorrectIndex());
                    String t = q.getText();
                    if (t.contains("צבוע")) {
                        assertThat(correct).as(t).isEqualTo(q.getVisual().getA() + "/" + q.getVisual().getB());
                    } else if (t.contains("הכי גדול")) {
                        double best = value(correct);
                        for (String o : q.getOptions()) if (!o.equals(correct)) assertThat(value(o)).as(t + " " + o).isLessThan(best);
                    } else if (t.contains("שווה ל")) {
                        Matcher m = FRAC.matcher(t); m.find();
                        double target = Double.parseDouble(m.group(1)) / Double.parseDouble(m.group(2));
                        assertThat(value(correct)).as(t).isCloseTo(target, org.assertj.core.data.Offset.offset(1e-9));
                        noOtherEqual(q, correct, t);
                    } else if (t.contains(" מ-")) {
                        Matcher m = FRAC.matcher(t); m.find();
                        int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2));
                        int whole = Integer.parseInt(t.replaceAll(".*מ-(\\d+).*", "$1"));
                        assertThat(whole % d).isZero();
                        assertThat(Integer.parseInt(correct)).as(t).isEqualTo(whole / d * n);
                    } else {
                        Matcher m = FRAC.matcher(t); m.find();
                        int a = Integer.parseInt(m.group(1)), da = Integer.parseInt(m.group(2));
                        m.find();
                        int b = Integer.parseInt(m.group(1)), db = Integer.parseInt(m.group(2));
                        double expected = t.contains(" - ") ? (double) a / da - (double) b / db : (double) a / da + (double) b / db;
                        assertThat(value(correct)).as(t).isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-9));
                        assertThat(expected).as(t).isGreaterThan(0);
                        noOtherEqual(q, correct, t);
                    }
                    assertThat(Tutor.explain(t, 0)).as("help for " + t).isNotNull();
                }
            }
        }
    }

    @Test
    void geometryIsAlwaysRight() {
        for (long seed = 0; seed < 400; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (int stage = 1; stage <= 6; stage++) {
                QuestionResponse q = gen.geometry(0, stage, true);
                checkShape(q);
                String t = q.getText();
                int answer = Integer.parseInt(q.getOptions().get(q.getCorrectIndex()));
                List<Integer> n = NUM.matcher(t).results().map(r -> Integer.parseInt(r.group())).toList();
                if (t.contains("צלעות")) {
                    assertThat(answer).isEqualTo(q.getVisual().getA());
                } else if (t.contains("שטח") && t.contains("משולש")) {
                    assertThat(answer * 2).as(t).isEqualTo(n.get(0) * n.get(1));
                } else if (t.contains("זווית")) {
                    assertThat(answer).as(t).isEqualTo(180 - n.get(0) - n.get(1)).isPositive();
                } else if (t.contains("ריבוע")) {
                    assertThat(answer).as(t).isEqualTo(t.contains("שטח") ? n.get(0) * n.get(0) : 4 * n.get(0));
                } else if (t.contains("היקף")) {
                    assertThat(answer).as(t).isEqualTo(2 * (n.get(0) + n.get(1)));
                } else {
                    assertThat(answer).as(t).isEqualTo(n.get(0) * n.get(1));
                }
                assertThat(Tutor.explain(t, 1)).as("help for " + t).isNotNull();
            }
        }
    }

    @Test
    void mixedPracticeNeverGetsAPictureQuestion() {
        for (long seed = 0; seed < 200; seed++) {
            FractionGeometryQuestions gen = new FractionGeometryQuestions(new Random(seed));
            for (int stage = 1; stage <= 6; stage++) {
                assertThat(gen.fraction(0, Grade.G5, stage, false).getText()).doesNotContain("צבוע");
                assertThat(gen.geometry(0, stage, false).getText()).doesNotContain("צלעות");
            }
        }
    }

    @Test
    void theHelpNeverReadsAFractionAsWholeNumbers() {
        assertThat(Tutor.explain("2/7 + 3/7 = ?", 0).text()).contains("רק את המספרים שלמעלה").doesNotContain("10");
    }

    private static void checkShape(QuestionResponse q) {
        assertThat(q.getOptions()).hasSize(4);
        assertThat(new HashSet<>(q.getOptions())).hasSize(4);
        assertThat(q.getCorrectIndex()).isBetween(0, 3);
        for (String o : q.getOptions()) {
            assertThat(o).as(q.getText()).matches("\\d+(/\\d+)?").doesNotContain("/0").doesNotStartWith("-");
        }
    }

    private static void noOtherEqual(QuestionResponse q, String correct, String t) {
        for (String o : q.getOptions()) {
            if (!o.equals(correct)) assertThat(value(o)).as(t + ": " + o + " equals " + correct).isNotCloseTo(value(correct),
                    org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    private static double value(String s) {
        if (!s.contains("/")) return Double.parseDouble(s);
        String[] p = s.split("/");
        return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
    }
}
