package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-11-24 (Shir, the workbook page "הרחבת שברים"): every question solved here independently. */
class FracExpandTest {

    private static final Pattern MISSING = Pattern.compile("השלימו את השבר: (\\d+)/(\\d+) = (\\?|\\d+)/(\\?|\\d+)");
    private static final Pattern HOW = Pattern.compile("בכמה הרחיבו\\? (\\d+)/(\\d+) = (\\d+)/(\\d+)");
    private static final Pattern WHICH = Pattern.compile("איזה שבר הוא הרחבה של (\\d+)/(\\d+)\\?");
    private static final Pattern PAIR = Pattern.compile("איזה זוג הורחב פי (\\d+)\\?");
    private static final Pattern FR = Pattern.compile("(\\d+)/(\\d+) = (\\d+)/(\\d+)");

    @Test
    void everyQuestionHasExactlyOneRightAnswer() {
        ExtraTopicsQuestions gen = new ExtraTopicsQuestions(new Random(7));
        for (Grade g : new Grade[] {Grade.G4, Grade.G5, Grade.G6}) {
            for (int stage = 1; stage <= 6; stage++) {
                for (int i = 0; i < 400; i++) {
                    QuestionResponse q = gen.question(i % 10, Topic.FRAC_EXPAND, g, stage, i % 3 == 0);
                    assertEquals(4, q.getOptions().size(), q.getText());
                    String right = q.getOptions().get(q.getCorrectIndex());
                    int rightCount = 0;
                    for (String o : q.getOptions()) if (isRight(q.getText(), o)) rightCount++;
                    assertTrue(isRight(q.getText(), right), q.getText() + " -> " + right);
                    assertEquals(1, rightCount, q.getText() + " " + q.getOptions());
                }
            }
        }
    }

    @Test
    void gradesBelowFourDontGetIt() {
        assertEquals(0, ExtraTopicsQuestions.levelCap(Topic.FRAC_EXPAND, Grade.G3));
        assertEquals(4, ExtraTopicsQuestions.levelCap(Topic.FRAC_EXPAND, Grade.G4));
    }

    @Test
    void addingInsteadOfMultiplyingIsNamed() {
        assertEquals(Mistakes.EXPAND_RULE, Mistakes.classify(Topic.FRAC_EXPAND, "איזה שבר הוא הרחבה של 2/3?", "6/9", "5/6"));
    }

    private static boolean isRight(String text, String o) {
        Matcher m = MISSING.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2)), x = Integer.parseInt(o);
            return m.group(3).equals("?") ? x * d == n * Integer.parseInt(m.group(4)) : n * x == d * Integer.parseInt(m.group(3));
        }
        m = HOW.matcher(text);
        if (m.find()) {
            int k = Integer.parseInt(o);
            return Integer.parseInt(m.group(1)) * k == Integer.parseInt(m.group(3)) && Integer.parseInt(m.group(2)) * k == Integer.parseInt(m.group(4));
        }
        m = WHICH.matcher(text);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1)), d = Integer.parseInt(m.group(2));
            String[] p = o.split("/");
            int a = Integer.parseInt(p[0].trim()), b = Integer.parseInt(p[1].trim());
            return a % n == 0 && a / n > 1 && a * d == b * n;
        }
        m = PAIR.matcher(text);
        if (m.find()) {
            int k = Integer.parseInt(m.group(1));
            Matcher f = FR.matcher(o);
            assertTrue(f.find(), o);
            return Integer.parseInt(f.group(1)) * k == Integer.parseInt(f.group(3)) && Integer.parseInt(f.group(2)) * k == Integer.parseInt(f.group(4));
        }
        fail("unknown question: " + text);
        return false;
    }
}
