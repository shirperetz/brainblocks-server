package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.dto.response.VisualModel;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates child-friendly Hebrew math questions for a given topic, grade, difficulty and
 * stage. Every question carries four options (one correct), a teaching hint, a short
 * explanation, and an optional visual model for younger children.
 *
 * Number ranges grow with the grade, the chosen difficulty, and the stage number (1-6),
 * so later stages within a topic feel a little harder.
 */
@Service
public class LearningQuestionService {

    private static final String[] NAMES = {"דני", "מאיה", "יוסי", "נועה", "איתי", "שירה", "תמר", "עומר"};
    /** Girls' names above - the story's verb follows the name ("נועה קיבלה", "דני קיבל"). */
    private static final Set<String> GIRLS = Set.of("מאיה", "נועה", "שירה", "תמר");
    private static final String[] OBJECTS = {"תפוחים", "בלונים", "עפרונות", "מדבקות", "כדורים", "ממתקים"};
    private static final String COUNT_EMOJI = "🍎";
    private static final String GROUP_EMOJI = "⭐";

    private final SecureRandom random = new SecureRandom();

    public List<QuestionResponse> generate(Topic topic, Grade grade, Difficulty difficulty, int stage, int count) {
        Grade g = grade == null ? Grade.G3 : grade;
        Difficulty d = difficulty == null ? Difficulty.MEDIUM : difficulty;
        int s = Math.min(Math.max(stage, 1), 6);

        List<QuestionResponse> questions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Topic actual = topic == Topic.MIXED ? randomTopicForGrade(g) : topic;
            questions.add(buildQuestion(i, actual, g, d, s));
        }
        return questions;
    }

    private Topic randomTopicForGrade(Grade grade) {
        List<Topic> pool = new ArrayList<>(List.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.COMPARISON, Topic.SEQUENCES));
        if (grade.ordinal() >= Grade.G3.ordinal()) {
            pool.add(Topic.MULTIPLICATION);
            pool.add(Topic.DIVISION);
        }
        if (grade.ordinal() >= Grade.G4.ordinal()) {
            pool.add(Topic.WORD_PROBLEMS);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private QuestionResponse buildQuestion(int id, Topic topic, Grade grade, Difficulty diff, int stage) {
        return switch (topic) {
            case ADDITION -> addition(id, grade, diff, stage);
            case SUBTRACTION -> subtraction(id, grade, diff, stage);
            case MULTIPLICATION -> multiplication(id, grade, diff, stage);
            case DIVISION -> division(id, grade, diff, stage);
            case COMPARISON -> comparison(id, grade, diff, stage);
            case SEQUENCES -> sequences(id, grade, diff, stage);
            case WORD_PROBLEMS -> wordProblem(id, grade, diff, stage);
            case MIXED -> addition(id, grade, diff, stage); // MIXED is resolved earlier; safety fallback
            // Island games build their own rounds in the client; a plain addition question is a safe fallback.
            case DRAGON, BALLOONS, BRIDGE, TRAIN -> addition(id, grade, diff, stage);
        };
    }

    // ---- Per-topic builders -------------------------------------------------

    private QuestionResponse addition(int id, Grade grade, Difficulty diff, int stage) {
        int max = scaled(additiveMax(grade), stage, diff);
        int a = rand(1, max);
        int b = rand(1, max);
        if (grade == Grade.G1) {
            // Grade א works within 20 - the sum too (was "20 + 1" at stage 6, found by a test).
            a = rand(1, Math.min(max, 19));
            b = rand(1, Math.min(max, 20 - a));
        }
        int result = a + b;
        VisualModel visual = result <= 10
                ? new VisualModel("OBJECTS", COUNT_EMOJI, a, b, result, "+")
                : null;
        return build(id, Topic.ADDITION,
                "כמה זה " + a + " + " + b + "?",
                result, additiveSpread(result),
                "תתחיל מ־" + a + " ותספור עוד " + b + " צעדים קדימה",
                a + " ועוד " + b + " זה " + result,
                visual);
    }

    private QuestionResponse subtraction(int id, Grade grade, Difficulty diff, int stage) {
        int max = scaled(additiveMax(grade), stage, diff);
        int a = rand(2, max);
        int b = rand(1, a);
        int result = a - b;
        VisualModel visual = a <= 10
                ? new VisualModel("TAKE_AWAY", COUNT_EMOJI, a, b, result, "-")
                : null;
        return build(id, Topic.SUBTRACTION,
                "כמה זה " + a + " - " + b + "?",
                result, additiveSpread(a),
                "תתחיל מ־" + a + " ותלך " + b + " צעדים אחורה",
                a + " פחות " + b + " זה " + result,
                visual);
    }

    private QuestionResponse multiplication(int id, Grade grade, Difficulty diff, int stage) {
        int max = Math.max(3, scaled(factorMax(grade), stage, diff));
        int a = rand(2, max);
        int b = rand(2, max);
        int result = a * b;
        VisualModel visual = (a <= 5 && b <= 5)
                ? new VisualModel("GROUPS", GROUP_EMOJI, a, b, result, "×")
                : null;
        return build(id, Topic.MULTIPLICATION,
                "כמה זה " + a + " × " + b + "?",
                result, Math.max(2, a + b),
                "זה כמו " + a + " קבוצות של " + b,
                a + " כפול " + b + " זה " + result,
                visual);
    }

    private QuestionResponse division(int id, Grade grade, Difficulty diff, int stage) {
        int max = Math.max(3, scaled(factorMax(grade), stage, diff));
        int b = rand(2, max);
        int result = rand(2, max);
        int a = b * result;
        return build(id, Topic.DIVISION,
                "כמה זה " + a + " ÷ " + b + "?",
                result, Math.max(2, result),
                "כמה פעמים " + b + " נכנס בתוך " + a + "?",
                a + " חלקי " + b + " זה " + result,
                null);
    }

    private QuestionResponse comparison(int id, Grade grade, Difficulty diff, int stage) {
        int max = scaled(additiveMax(grade), stage, diff);
        // Four distinct numbers; the child picks the biggest.
        Set<Integer> nums = new LinkedHashSet<>();
        while (nums.size() < 4) {
            nums.add(rand(1, max));
        }
        List<Integer> options = new ArrayList<>(nums);
        int correct = Collections.max(options);
        List<String> strOptions = options.stream().map(String::valueOf).toList();
        return new QuestionResponse(id, Topic.COMPARISON,
                "איזה המספר הגדול ביותר?",
                strOptions, options.indexOf(correct),
                "כמה ספרות יש בכל מספר? יותר ספרות = מספר גדול יותר",
                "המספר הגדול ביותר הוא " + correct,
                null);
    }

    private QuestionResponse sequences(int id, Grade grade, Difficulty diff, int stage) {
        int step = grade.ordinal() <= Grade.G1.ordinal() ? rand(1, 3) : rand(1, 5);
        int start = rand(1, Math.max(2, additiveMax(grade) / 4));
        int t1 = start, t2 = start + step, t3 = start + 2 * step, t4 = start + 3 * step;
        int next = start + 4 * step;
        return build(id, Topic.SEQUENCES,
                "מה המספר הבא? " + t1 + ", " + t2 + ", " + t3 + ", " + t4 + ", ?",
                next, Math.max(2, step + 1),
                "תבדוק כמה מוסיפים בכל פעם",
                "מוסיפים " + step + " בכל פעם, אז הבא הוא " + next,
                null);
    }

    private QuestionResponse wordProblem(int id, Grade grade, Difficulty diff, int stage) {
        String name = pick(NAMES);
        String obj = pick(OBJECTS);
        boolean canMultiply = grade.ordinal() >= Grade.G3.ordinal();
        int kind = random.nextInt(canMultiply ? 3 : 2); // 0=add, 1=sub, 2=mul
        return switch (kind) {
            case 1 -> {
                int a = rand(5, 20);
                int b = rand(1, a);
                int result = a - b;
                yield build(id, Topic.WORD_PROBLEMS,
                        "ל" + name + " היו " + a + " " + obj + ". " + b + " " + obj + " הלכו לאיבוד. כמה נשארו?",
                        result, additiveSpread(a),
                        "מתחילים מ־" + a + " ומורידים " + b,
                        a + " פחות " + b + " זה " + result, null);
            }
            case 2 -> {
                int a = rand(2, 6);
                int b = rand(2, 6);
                int result = a * b;
                yield build(id, Topic.WORD_PROBLEMS,
                        "ל" + name + " יש " + a + " שקיות, ובכל שקית " + b + " " + obj + ". כמה " + obj + " יש בסך הכל?",
                        result, Math.max(2, a + b),
                        "זה כמו " + a + " קבוצות של " + b,
                        a + " כפול " + b + " זה " + result, null);
            }
            default -> {
                int a = rand(3, 20);
                int b = rand(1, 15);
                int result = a + b;
                yield build(id, Topic.WORD_PROBLEMS,
                        "ל" + name + " יש " + a + " " + obj + ", " + (GIRLS.contains(name) ? "וקיבלה" : "וקיבל") + " עוד " + b + ". כמה " + obj + " יש ל" + name + " עכשיו?",
                        result, additiveSpread(result),
                        "מחברים " + a + " ועוד " + b,
                        a + " ועוד " + b + " זה " + result, null);
            }
        };
    }

    // ---- Helpers ------------------------------------------------------------

    /** Builds a numeric question: correct answer + 3 nearby distractors, shuffled. */
    private QuestionResponse build(int id, Topic topic, String text, int correct, int spread,
                                   String hint, String explanation, VisualModel visual) {
        List<Integer> options = new ArrayList<>(distractors(correct, spread, 3));
        options.add(correct);
        Collections.shuffle(options, random);
        List<String> strOptions = options.stream().map(String::valueOf).toList();
        return new QuestionResponse(id, topic, text, strOptions, options.indexOf(correct), hint, explanation, visual);
    }

    /** Three unique wrong answers near {@code correct}, never negative. */
    private List<Integer> distractors(int correct, int spread, int needed) {
        int s = Math.max(1, spread);
        Set<Integer> set = new LinkedHashSet<>();
        int guard = 0;
        while (set.size() < needed && guard++ < 200) {
            int cand = correct + (random.nextInt(2 * s + 1) - s);
            if (cand != correct && cand >= 0) {
                set.add(cand);
            }
        }
        int extra = 1;
        while (set.size() < needed) {
            int cand = correct + extra;
            if (cand >= 0 && cand != correct) set.add(cand);
            extra++;
        }
        return new ArrayList<>(set);
    }

    private int additiveMax(Grade grade) {
        return switch (grade) {
            case G1 -> 20;
            case G2 -> 100;
            case G3 -> 200;
            case G4 -> 1000;
            case G5 -> 2000;
            case G6 -> 10000;
        };
    }

    private int factorMax(Grade grade) {
        return switch (grade) {
            case G1, G2 -> 5;
            case G3 -> 10;
            case G4 -> 12;
            case G5 -> 15;
            case G6 -> 20;
        };
    }

    /** Scales a grade's ceiling by stage (1-6) and difficulty, never below a small floor. */
    private int scaled(int base, int stage, Difficulty diff) {
        double stageScale = Math.min(1.0, 0.45 + 0.10 * stage);
        double diffScale = switch (diff) {
            case EASY -> 0.7;
            case MEDIUM -> 1.0;
            case HARD -> 1.25;
        };
        return Math.max(5, (int) Math.round(base * stageScale * diffScale));
    }

    private int additiveSpread(int value) {
        if (value <= 20) return 3;
        if (value <= 100) return 8;
        return Math.max(10, value / 8);
    }

    private int rand(int minInclusive, int maxInclusive) {
        if (maxInclusive <= minInclusive) return minInclusive;
        return minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }

    private String pick(String[] arr) {
        return arr[random.nextInt(arr.length)];
    }
}
