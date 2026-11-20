package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-11-21: the new topics (ExtraTopicsQuestions) - every grade that has the topic, every stage,
 * many questions: well-formed, and where the question can be solved from its text, THIS test solves
 * it on its own and checks the marked answer (order of operations, x, rounding, LCM/GCD, primes,
 * decimals, percents, unit conversions).
 */
class ExtraTopicsTest {

    static final List<Topic> NEW = List.of(Topic.EVEN_ODD, Topic.MAKE_TEN, Topic.ORDER_OPS, Topic.ROUNDING,
            Topic.DIVISIBILITY, Topic.MEASURE, Topic.DECIMALS, Topic.PERCENT, Topic.RATIO, Topic.DATA,
            Topic.CIRCLE, Topic.VOLUME, Topic.EQUATIONS);

    final LearningQuestionService service = new LearningQuestionService();

    @Test
    void everyQuestionIsWellFormedAndRight() {
        int checked = 0, solved = 0;
        for (Topic t : NEW) {
            boolean anyGrade = false;
            for (Grade g : Grade.values()) {
                if (!GradeTopics.allowed(t, g)) continue;
                anyGrade = true;
                for (int stage = 1; stage <= 6; stage++) {
                    for (Difficulty d : Difficulty.values()) {
                        for (QuestionResponse q : service.generate(t, g, d, stage, 20)) {
                            String where = t + " " + g + " s" + stage + ": " + q.getText() + " " + q.getOptions();
                            assertEquals(t, q.getTopic(), where);
                            assertFalse(q.getText().isBlank(), where);
                            assertNotNull(q.getHint(), where);
                            assertNotNull(q.getExplanation(), where);
                            assertTrue(q.getOptions().size() >= 2 && q.getOptions().size() <= 4, where);
                            assertEquals(q.getOptions().size(), new HashSet<>(q.getOptions()).size(), "duplicate option: " + where);
                            assertTrue(q.getCorrectIndex() >= 0 && q.getCorrectIndex() < q.getOptions().size(), where);
                            for (String o : q.getOptions()) {
                                assertFalse(o.isBlank(), where);
                                assertFalse(o.matches(".*\\(\\d+\\)$"), "filler option: " + where);
                                assertFalse(o.startsWith("-"), "negative option: " + where);
                                assertFalse(o.contains("null") || o.contains("NaN"), where);
                            }
                            if (t != Topic.EVEN_ODD && t != Topic.DATA && t != Topic.DECIMALS || q.getOptions().size() == 4) {
                                // yes/no style questions may have 2-3 options; the rest always 4
                                if (!q.getText().contains("זוגית או אי-זוגית") && !q.getText().startsWith("איזה מספר גדול")
                                        && !q.getText().startsWith("איזה מספר קטן") && !q.getText().contains("הכי סביר")) {
                                    assertEquals(4, q.getOptions().size(), where);
                                }
                            }
                            assertNotNull(q.getSkill(), where);
                            assertTrue(Skills.known(q.getSkill()), "skill without a name: " + q.getSkill() + " " + where);
                            String right = q.getOptions().get(q.getCorrectIndex());
                            String mine = solve(q.getText());
                            if (mine != null) {
                                solved++;
                                assertEquals(norm(mine), norm(right), "wrong answer marked: " + where);
                                for (int i = 0; i < q.getOptions().size(); i++) {
                                    if (i != q.getCorrectIndex()) assertNotEquals(norm(mine), norm(q.getOptions().get(i)), "two right answers: " + where);
                                }
                            }
                            checked++;
                        }
                    }
                }
            }
            assertTrue(anyGrade, t + " belongs to no grade");
        }
        System.out.println("ExtraTopicsTest: " + checked + " questions, " + solved + " solved independently");
        assertTrue(solved > checked / 4, "too few solved independently: " + solved + "/" + checked);
    }

    /** Each grade gets only its own levels: e.g. ו׳ topics are not ה׳ material, א׳ has no primes. */
    @Test
    void gradesGetOnlyTheirMaterial() {
        assertFalse(GradeTopics.allowed(Topic.PERCENT, Grade.G5));
        assertTrue(GradeTopics.allowed(Topic.PERCENT, Grade.G6));
        assertFalse(GradeTopics.allowed(Topic.DIVISIBILITY, Grade.G1));
        assertTrue(GradeTopics.allowed(Topic.EVEN_ODD, Grade.G1));
        assertFalse(GradeTopics.allowed(Topic.CIRCLE, Grade.G4));
        // stage 6 of a ד׳ child in divisibility = level 4 (divisors), never ה׳'s primes
        for (QuestionResponse q : service.generate(Topic.DIVISIBILITY, Grade.G4, Difficulty.HARD, 6, 20)) {
            assertNotEquals("PRIMES", q.getSkill(), q.getText());
            assertNotEquals("GCD", q.getSkill(), q.getText());
        }
    }

    /** HARD never goes past the grade's number range (Shir: harder, but not another grade's material). */
    @Test
    void hardStaysInsideTheGrade() {
        Pattern p = Pattern.compile("כמה זה (\\d+) \\+ (\\d+)\\?");
        for (int k = 0; k < 30; k++) {
            for (QuestionResponse q : service.generate(Topic.ADDITION, Grade.G1, Difficulty.HARD, 6, 10)) {
                Matcher m = p.matcher(q.getText());
                if (m.find()) {
                    int sum = Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
                    assertTrue(sum <= 20, "grade 1 hard went past 20: " + q.getText());
                }
            }
        }
    }

    // ---- an independent solver for the question forms that can be solved from the text ----

    static String norm(String s) {
        String t = s.trim();
        try { return new BigDecimal(t).stripTrailingZeros().toPlainString(); } catch (NumberFormatException e) { return t; }
    }

    static String solve(String text) {
        Matcher m;
        if ((m = Pattern.compile("^כמה זה ([0-9 +\\-×÷()]+)\\?$").matcher(text)).find()) return String.valueOf(eval(m.group(1)));
        if ((m = Pattern.compile("^מה x\\? (.+) = (\\d+)$").matcher(text)).find()) {
            String lhs = m.group(1);
            long r = Long.parseLong(m.group(2));
            for (long x = 0; x <= 2000; x++) if (eval(lhs.replace("x", String.valueOf(x))) == r) return String.valueOf(x);
            fail("no x for " + text);
        }
        if ((m = Pattern.compile("^איזה מספר חסר\\? (\\d+) \\+ \\? = (\\d+)$").matcher(text)).find()
                || (m = Pattern.compile("^איזה מספר חסר\\? \\? \\+ (\\d+) = (\\d+)$").matcher(text)).find())
            return String.valueOf(Long.parseLong(m.group(2)) - Long.parseLong(m.group(1)));
        if ((m = Pattern.compile("^עגלו את (\\d+) (לעשרות|למאות|לאלפים)").matcher(text)).find()) {
            int to = m.group(2).equals("לעשרות") ? 10 : m.group(2).equals("למאות") ? 100 : 1000;
            long n = Long.parseLong(m.group(1));
            return String.valueOf((n + to / 2) / to * to);
        }
        if ((m = Pattern.compile("^מה הכפולה המשותפת הקטנה ביותר של (\\d+) ושל (\\d+)\\?$").matcher(text)).find()) {
            long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(2));
            for (long x = Math.max(a, b); ; x++) if (x % a == 0 && x % b == 0) return String.valueOf(x);
        }
        if ((m = Pattern.compile("^מה המחלק המשותף הגדול ביותר של (\\d+) ושל (\\d+)\\?$").matcher(text)).find()) {
            long a = Long.parseLong(m.group(1)), b = Long.parseLong(m.group(2));
            for (long x = Math.min(a, b); ; x--) if (a % x == 0 && b % x == 0) return String.valueOf(x);
        }
        if ((m = Pattern.compile("^([0-9.]+) ([+\\-×÷]) ([0-9.]+) = \\?$").matcher(text)).find()) {
            BigDecimal a = new BigDecimal(m.group(1)), b = new BigDecimal(m.group(3));
            BigDecimal r = switch (m.group(2)) {
                case "+" -> a.add(b);
                case "-" -> a.subtract(b);
                case "×" -> a.multiply(b);
                default -> a.divide(b);
            };
            return r.stripTrailingZeros().toPlainString();
        }
        if ((m = Pattern.compile("^כמה זה (\\d+)% מ-(\\d+)\\?$").matcher(text)).find())
            return String.valueOf(Long.parseLong(m.group(1)) * Long.parseLong(m.group(2)) / 100);
        if ((m = Pattern.compile("^(\\d+) מטר = כמה סנטימטרים\\?$").matcher(text)).find()) return String.valueOf(Long.parseLong(m.group(1)) * 100);
        if ((m = Pattern.compile("^(\\d+) שעות = כמה דקות\\?$").matcher(text)).find()) return String.valueOf(Long.parseLong(m.group(1)) * 60);
        if ((m = Pattern.compile("^(\\d+) (קילוגרם|ליטר|קילומטר) = כמה").matcher(text)).find()) return String.valueOf(Long.parseLong(m.group(1)) * 1000);
        if ((m = Pattern.compile("^קובייה שצלעה (\\d+) ס״מ. מה הנפח").matcher(text)).find()) {
            long s = Long.parseLong(m.group(1));
            return String.valueOf(s * s * s);
        }
        if ((m = Pattern.compile("^תיבה: אורך (\\d+), רוחב (\\d+) וגובה (\\d+)").matcher(text)).find())
            return String.valueOf(Long.parseLong(m.group(1)) * Long.parseLong(m.group(2)) * Long.parseLong(m.group(3)));
        if (text.equals("איזה מספר הוא ראשוני?")) return null; // checked by options below
        return null;
    }

    @Test
    void primeQuestionsHaveExactlyOnePrime() {
        for (int k = 0; k < 40; k++) {
            for (QuestionResponse q : service.generate(Topic.DIVISIBILITY, Grade.G6, Difficulty.MEDIUM, 6, 20)) {
                if (!q.getText().equals("איזה מספר הוא ראשוני?")) continue;
                List<Integer> primes = new ArrayList<>();
                for (int i = 0; i < q.getOptions().size(); i++) if (prime(Integer.parseInt(q.getOptions().get(i)))) primes.add(i);
                assertEquals(List.of(q.getCorrectIndex()), primes, q.getOptions().toString());
            }
            for (QuestionResponse q : service.generate(Topic.DIVISIBILITY, Grade.G6, Difficulty.MEDIUM, 2, 20)) {
                Matcher m = Pattern.compile("מתחלק ב-(\\d+)").matcher(q.getText());
                if (!m.find()) continue;
                int d = Integer.parseInt(m.group(1));
                for (int i = 0; i < q.getOptions().size(); i++) {
                    boolean divisible = Integer.parseInt(q.getOptions().get(i)) % d == 0;
                    assertEquals(i == q.getCorrectIndex(), divisible, q.getText() + " " + q.getOptions());
                }
            }
        }
    }

    static boolean prime(int n) {
        if (n < 2) return false;
        for (int d = 2; d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    /** + - × ÷ and brackets with the usual order (a tiny recursive-descent parser). */
    static long eval(String s) {
        return new Object() {
            int i = 0;
            final String e = s.replace(" ", "");

            long expr() {
                long v = term();
                while (i < e.length() && (e.charAt(i) == '+' || e.charAt(i) == '-')) {
                    char op = e.charAt(i++);
                    long w = term();
                    v = op == '+' ? v + w : v - w;
                }
                return v;
            }

            long term() {
                long v = factor();
                while (i < e.length() && (e.charAt(i) == '×' || e.charAt(i) == '÷')) {
                    char op = e.charAt(i++);
                    long w = factor();
                    if (op == '÷') { if (w == 0 || v % w != 0) return Long.MIN_VALUE; v = v / w; } else v = v * w;
                }
                return v;
            }

            long factor() {
                if (e.charAt(i) == '(') { i++; long v = expr(); i++; return v; }
                int j = i;
                while (i < e.length() && Character.isDigit(e.charAt(i))) i++;
                return Long.parseLong(e.substring(j, i));
            }
        }.expr();
    }
}
