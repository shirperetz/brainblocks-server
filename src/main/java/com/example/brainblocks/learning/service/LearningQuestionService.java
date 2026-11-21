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
    private final FractionGeometryQuestions fractionGeometry = new FractionGeometryQuestions(random);
    private final ExtraTopicsQuestions extra = new ExtraTopicsQuestions(random);

    /** How many extra draws per slot before a round accepts a repeat (a topic with few possible exercises). */
    static final int FRESH_TRIES = 8;

    /**
     * 2026-11-12 (Shir: "always different exercises, unless the child got it wrong"): a round with no
     * exercise twice, and none of [avoid] (what this child answered right lately in this topic) - as long
     * as the topic has enough different exercises; a small topic (e.g. א׳ stage 1) never gets stuck, it
     * takes the least-repeated one. Slot by slot, so the first questions stay the small-number ones.
     */
    public List<QuestionResponse> generateFresh(Topic topic, Grade grade, Difficulty difficulty, int stage, int count,
                                                Set<String> avoid) {
        List<List<QuestionResponse>> draws = new ArrayList<>();
        for (int k = 0; k <= FRESH_TRIES; k++) draws.add(generate(topic, grade, difficulty, stage, count));
        Set<String> used = new java.util.HashSet<>();
        List<QuestionResponse> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            QuestionResponse pick = null;
            // 1st choice: new in this round and not answered right lately; 2nd: at least new in this round.
            for (int pass = 0; pass < 2 && pick == null; pass++) {
                for (List<QuestionResponse> d : draws) {
                    QuestionResponse q = d.get(i);
                    String key = key(q);
                    if (!used.contains(key) && (pass == 1 || avoid == null || !avoid.contains(key))) {
                        pick = q;
                        break;
                    }
                }
            }
            if (pick == null) pick = draws.get(0).get(i);
            used.add(key(pick));
            out.add(pick);
        }
        return out;
    }

    /**
     * The same exercise = the same words AND the same right answer ("כמה צלעות יש לצורה?" with a
     * triangle and with a hexagon are two exercises). Spaces don't make it new.
     */
    public static String key(String text, String correct) {
        return (text == null ? "" : text.replaceAll("\\s+", " ").strip()) + "|" + (correct == null ? "" : correct.strip());
    }

    public static String key(QuestionResponse q) {
        return key(q.getText(), q.getOptions().get(q.getCorrectIndex()));
    }

    public List<QuestionResponse> generate(Topic topic, Grade grade, Difficulty difficulty, int stage, int count) {
        Grade g = grade == null ? Grade.G3 : grade;
        Difficulty d = difficulty == null ? Difficulty.MEDIUM : difficulty;
        int s = Math.min(Math.max(stage, 1), 6);

        List<QuestionResponse> questions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Topic actual = topic == Topic.MIXED ? randomTopicForGrade(g) : topic == Topic.MEITZAV ? meitzavTopic(g) : topic;
            // Mixed practice, daily challenge and 60 seconds show no picture - picture questions stay out.
            questions.add(actual == Topic.FRACTIONS ? fractionGeometry.fraction(i, g, s, topic != Topic.MIXED)
                    : actual == Topic.GEOMETRY ? fractionGeometry.geometry(i, s, topic != Topic.MIXED)
                    : actual == Topic.ANGLES || actual == Topic.ANGLE_CALC || actual == Topic.PERIMETER || actual == Topic.AREA
                            ? fractionGeometry.geometryTrack(i, actual, s, true, g)
                    : ExtraTopicsQuestions.handles(actual) ? extra.question(i, actual, g, s, d == Difficulty.HARD)
                    : buildQuestion(i, actual, g, d, s));
        }
        // 2026-11-11: every question says which skill it practices (fractions/geometry tag themselves).
        for (QuestionResponse q : questions) {
            if (q.getSkill() == null) q.withSkill(Skills.of(null, q.getTopic(), s, q.getText()));
        }
        return questions;
    }

    /**
     * 2026-11-21: the grade-5 מיצ"ב weights (ראמ"ה, מפרט תש"ף): fractions and decimals ~35%, whole numbers
     * ~30%, geometry and measurement ~25%, data and chance ~10%. Only topics of the child's grade.
     */
    private static final Topic[][] MEITZAV_PARTS = {
            {Topic.FRACTIONS, Topic.DECIMALS},
            {Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION, Topic.DIVISION, Topic.WORD_PROBLEMS,
                    Topic.ORDER_OPS, Topic.DIVISIBILITY, Topic.ROUNDING},
            {Topic.ANGLES, Topic.ANGLE_CALC, Topic.PERIMETER, Topic.AREA, Topic.MEASURE, Topic.VOLUME},
            {Topic.DATA},
    };
    private static final int[] MEITZAV_WEIGHTS = {35, 30, 25, 10};

    Topic meitzavTopic(Grade grade) {
        int r = random.nextInt(100), part = 0;
        for (int acc = MEITZAV_WEIGHTS[0]; r >= acc && part < 3; acc += MEITZAV_WEIGHTS[++part]) { }
        List<Topic> pool = new ArrayList<>();
        for (Topic t : MEITZAV_PARTS[part]) if (GradeTopics.allowed(t, grade)) pool.add(t);
        if (pool.isEmpty()) pool.add(Topic.ADDITION);
        return pool.get(random.nextInt(pool.size()));
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
        if (grade.ordinal() >= Grade.G5.ordinal()) {
            pool.add(Topic.FRACTIONS);
        }
        if (grade.ordinal() >= Grade.G5.ordinal()) {
            pool.add(Topic.GEOMETRY); // 2026-11-11: גיאומטריה גם לכיתה ה׳
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private QuestionResponse buildQuestion(int id, Topic topic, Grade grade, Difficulty diff, int stage) {
        return switch (topic) {
            // 2026-11-11 ("שיהיה מכל הסוגים"): from level 2, about a third are "what is missing?".
            case ADDITION, SUBTRACTION, MULTIPLICATION, DIVISION -> stage >= 2 && random.nextInt(10) < 3
                    ? missing(arith(id, topic, grade, diff, stage))
                    : arith(id, topic, grade, diff, stage);
            case COMPARISON -> comparison(id, grade, diff, stage);
            case SEQUENCES -> sequences(id, grade, diff, stage);
            case WORD_PROBLEMS -> wordProblem(id, grade, diff, stage);
            case MIXED, MEITZAV -> addition(id, grade, diff, stage); // resolved earlier; safety fallback
            case FRACTIONS -> fractionGeometry.fraction(id, grade, stage, true);
            case GEOMETRY -> fractionGeometry.geometry(id, stage, true);
            case ANGLES, ANGLE_CALC, PERIMETER, AREA -> fractionGeometry.geometryTrack(id, topic, stage, true, grade);
            case EVEN_ODD, MAKE_TEN, ORDER_OPS, ROUNDING, DIVISIBILITY, MEASURE, DECIMALS, PERCENT, RATIO,
                 DATA, CIRCLE, VOLUME, EQUATIONS -> extra.question(id, topic, grade, stage, diff == Difficulty.HARD);
            // Island games build their own rounds in the client; a plain addition question is a safe fallback.
            case DRAGON, BALLOONS, BRIDGE, TRAIN, CLOCK, SHOP, COINS,
                 SHAPE_NAME, SHAPE_SIDES, SHAPE_PATTERN, GROUPS, SHARING, ARRAYS, PLACE_VALUE, NUMBER_LINE, BIGGER -> addition(id, grade, diff, stage);
        };
    }

    private QuestionResponse arith(int id, Topic topic, Grade grade, Difficulty diff, int stage) {
        return switch (topic) {
            case SUBTRACTION -> subtraction(id, grade, diff, stage);
            case MULTIPLICATION -> multiplication(id, grade, diff, stage);
            case DIVISION -> division(id, grade, diff, stage);
            default -> addition(id, grade, diff, stage);
        };
    }

    private static final java.util.regex.Pattern PLAIN = java.util.regex.Pattern.compile("כמה זה (\\d+) ([+\\-×÷]) (\\d+)\\?");

    /**
     * "כמה זה 7 + 5?" -> "מה המספר החסר? 7 + ? = 12" (or "? + 5 = 12"): same numbers, the child finds a
     * part instead of the whole. The answer is the hidden number; the options are rebuilt around it.
     */
    private QuestionResponse missing(QuestionResponse q) {
        java.util.regex.Matcher m = PLAIN.matcher(q.getText());
        if (!m.matches()) return q;
        int a = Integer.parseInt(m.group(1));
        int b = Integer.parseInt(m.group(3));
        String op = m.group(2);
        int r = Integer.parseInt(q.getOptions().get(q.getCorrectIndex()));
        boolean hideFirst = random.nextBoolean();
        int hidden = hideFirst ? a : b;
        String text = "מה המספר החסר? " + (hideFirst ? "?" : String.valueOf(a)) + " " + op + " " + (hideFirst ? String.valueOf(b) : "?") + " = " + r;
        String hint = switch (op) {
            case "+" -> hideFirst ? "כמה צריך להוסיף ל-" + b + " כדי להגיע ל-" + r + "?" : "מ-" + a + " ל-" + r + ": כמה צעדים קדימה?";
            case "-" -> hideFirst ? "מאיזה מספר מורידים " + b + " ומקבלים " + r + "? חברו " + r + " ועוד " + b + "."
                    : "כמה הורדנו מ-" + a + " כדי להגיע ל-" + r + "?";
            case "×" -> "איזה מספר כפול " + (hideFirst ? b : a) + " נותן " + r + "?";
            default -> hideFirst ? "איזה מספר מחלקים ב-" + b + " ומקבלים " + r + "? כפלו " + b + " ב-" + r + "."
                    : "את " + a + " מחלקים לקבוצות שוות ומקבלים " + r + ". בכמה מחלקים?";
        };
        return build(q.getId(), q.getTopic(), text, hidden,
                op.equals("+") || op.equals("-") ? additivePool(hidden, a >= 10 || b >= 10 || r >= 10) : nearPool(hidden, 1), hint,
                (hideFirst ? hidden + " " + op + " " + b : a + " " + op + " " + hidden) + " = " + r, null);
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
                result, additivePool(result, a >= 10 || b >= 10),
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
                result, additivePool(result, a >= 10 || b >= 10),
                "תתחיל מ־" + a + " ותלך " + b + " צעדים אחורה",
                a + " פחות " + b + " זה " + result,
                visual);
    }

    /** 2026-11-12 (curriculum): ב׳ learns multiplication/division as groups - the 2, 5 and 10 tables only. */
    private static final int[] G2_TABLES = {2, 5, 10};

    private QuestionResponse multiplication(int id, Grade grade, Difficulty diff, int stage) {
        int max = Math.max(3, scaled(factorMax(grade), stage, diff));
        int a = grade == Grade.G2 ? G2_TABLES[random.nextInt(stage <= 2 ? 2 : 3)] : rand(2, max);
        int b = grade == Grade.G2 ? rand(2, 10) : rand(2, max);
        int result = a * b;
        VisualModel visual = (a <= 5 && b <= 5)
                ? new VisualModel("GROUPS", GROUP_EMOJI, a, b, result, "×")
                : null;
        return build(id, Topic.MULTIPLICATION,
                "כמה זה " + a + " × " + b + "?",
                result, multiplicationPool(a, b),
                "זה כמו " + a + " קבוצות של " + b,
                a + " כפול " + b + " זה " + result,
                visual);
    }

    private QuestionResponse division(int id, Grade grade, Difficulty diff, int stage) {
        int max = Math.max(3, scaled(factorMax(grade), stage, diff));
        int b = grade == Grade.G2 ? G2_TABLES[random.nextInt(stage <= 2 ? 2 : 3)] : rand(2, max);
        int result = grade == Grade.G2 ? rand(2, 10) : rand(2, max);
        int a = b * result;
        return build(id, Topic.DIVISION,
                "כמה זה " + a + " ÷ " + b + "?",
                result, nearPool(result, 1),
                "כמה פעמים " + b + " נכנס בתוך " + a + "?",
                a + " חלקי " + b + " זה " + result,
                null);
    }

    private QuestionResponse comparison(int id, Grade grade, Difficulty diff, int stage) {
        int max = scaled(additiveMax(grade), stage, diff);
        // Four distinct numbers; the child picks the biggest.
        Set<Integer> nums = new LinkedHashSet<>();
        // 2026-11-11: from level 3 all four have the same number of digits - "the longest one" stops working.
        int digits = String.valueOf(max).length();
        int from = stage >= 3 && max >= 20 ? (int) Math.pow(10, digits - 1) : 1;
        if (from >= max) from = Math.max(1, from / 10);
        int guard = 0;
        while (nums.size() < 4 && guard++ < 1000) {
            nums.add(rand(from, max));
        }
        for (int x = from; nums.size() < 4; x++) nums.add(x);
        List<Integer> options = new ArrayList<>(nums);
        // 2026-11-11: from level 2, half ask for the smallest.
        boolean smallest = stage >= 2 && random.nextBoolean();
        int correct = smallest ? Collections.min(options) : Collections.max(options);
        List<String> strOptions = options.stream().map(String::valueOf).toList();
        if (smallest) {
            return new QuestionResponse(id, Topic.COMPARISON, "איזה המספר הקטן ביותר?", strOptions, options.indexOf(correct),
                    "כמה ספרות יש בכל מספר? פחות ספרות = מספר קטן יותר", "המספר הקטן ביותר הוא " + correct, null);
        }
        return new QuestionResponse(id, Topic.COMPARISON,
                "איזה המספר הגדול ביותר?",
                strOptions, options.indexOf(correct),
                "כמה ספרות יש בכל מספר? יותר ספרות = מספר גדול יותר",
                "המספר הגדול ביותר הוא " + correct,
                null);
    }

    private QuestionResponse sequences(int id, Grade grade, Difficulty diff, int stage) {
        int step = grade.ordinal() <= Grade.G1.ordinal() ? rand(1, 3) : rand(1, 5);
        if (grade.ordinal() >= Grade.G3.ordinal() && stage >= 4) step = rand(2, grade.ordinal() >= Grade.G5.ordinal() ? 25 : 10);
        // 2026-11-11: from level 3, some sequences go down (always staying at 0 or above).
        boolean down = stage >= 3 && random.nextInt(10) < 4;
        int start = rand(1, Math.max(2, additiveMax(grade) / 4)) + (down ? 4 * step : 0);
        if (down && start - 4 * step == step) start++; // the answer must not equal the step (the help names the step)
        int s = down ? -step : step;
        int t1 = start, t2 = start + s, t3 = start + 2 * s, t4 = start + 3 * s;
        int next = start + 4 * s;
        return build(id, Topic.SEQUENCES,
                "מה המספר הבא? " + t1 + ", " + t2 + ", " + t3 + ", " + t4 + ", ?",
                next, sequencePool(next, step),
                down ? "תבדוק כמה מורידים בכל פעם" : "תבדוק כמה מוסיפים בכל פעם",
                (down ? "מורידים " : "מוסיפים ") + step + " בכל פעם, אז הבא הוא " + next,
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
                        result, additivePool(result, a >= 10 || b >= 10),
                        "מתחילים מ־" + a + " ומורידים " + b,
                        a + " פחות " + b + " זה " + result, null);
            }
            case 2 -> {
                int a = rand(2, 6);
                int b = rand(2, 6);
                int result = a * b;
                yield build(id, Topic.WORD_PROBLEMS,
                        "ל" + name + " יש " + a + " שקיות, ובכל שקית " + b + " " + obj + ". כמה " + obj + " יש בסך הכל?",
                        result, multiplicationPool(a, b),
                        "זה כמו " + a + " קבוצות של " + b,
                        a + " כפול " + b + " זה " + result, null);
            }
            default -> {
                int a = rand(3, 20);
                int b = rand(1, 15);
                int result = a + b;
                yield build(id, Topic.WORD_PROBLEMS,
                        "ל" + name + " יש " + a + " " + obj + ", " + (GIRLS.contains(name) ? "וקיבלה" : "וקיבל") + " עוד " + b + ". כמה " + obj + " יש ל" + name + " עכשיו?",
                        result, additivePool(result, a >= 10 || b >= 10),
                        "מחברים " + a + " ועוד " + b,
                        a + " ועוד " + b + " זה " + result, null);
            }
        };
    }

    // ---- Helpers ------------------------------------------------------------

    /**
     * Builds a numeric question: the correct answer + 3 wrong ones from [pool], shuffled.
     * <p>
     * 2026-11-11 (Shir: "be a child and outsmart it - children must really calculate"). A "sneaky
     * child" bot (KidTricksAuditTest) beat the old random-nearby wrong answers WITHOUT solving:
     * computing only the last digit got 90% of addition/subtraction, "pick the one in the middle"
     * 50-61% almost everywhere, a rough estimate 75% of division. Now the wrong answers are the
     * mistakes children really make (a forgotten carry = ±10, the next row of the times table, one
     * step too many), and the correct answer's place among them (smallest ... biggest) is random.
     */
    private QuestionResponse build(int id, Topic topic, String text, int correct, List<Integer> pool,
                                   String hint, String explanation, VisualModel visual) {
        // A pool of exactly three is already the chosen set (additivePool places the right answer itself).
        List<Integer> options = new ArrayList<>(pool.size() == 3 && pool.stream().allMatch(x -> x >= 0 && x != correct)
                && new LinkedHashSet<>(pool).size() == 3 ? pool : pickBalanced(correct, pool));
        options.add(correct);
        Collections.shuffle(options, random);
        List<String> strOptions = options.stream().map(String::valueOf).toList();
        return new QuestionResponse(id, topic, text, strOptions, options.indexOf(correct), hint, explanation, visual);
    }

    /**
     * Three wrong answers from the pool so that the correct one is equally often the smallest, 2nd,
     * 3rd or biggest - "pick the middle" / "pick the biggest" stop working. Topped up with ±1, ±2...
     */
    private List<Integer> pickBalanced(int correct, List<Integer> pool) {
        List<Integer> below = new ArrayList<>(), above = new ArrayList<>();
        for (int x : new LinkedHashSet<>(pool)) {
            if (x < 0 || x == correct) continue;
            (x < correct ? below : above).add(x);
        }
        for (int extra = 1; below.size() + above.size() < 3 || above.size() < 3 - Math.min(3, below.size()); extra++) {
            if (!above.contains(correct + extra) && !below.contains(correct + extra)) above.add(correct + extra);
        }
        int lo = Math.max(0, 3 - above.size()), hi = Math.min(3, below.size());
        int k = lo + random.nextInt(hi - lo + 1); // how many wrong answers are smaller than the right one
        Collections.shuffle(below, random);
        Collections.shuffle(above, random);
        List<Integer> out = new ArrayList<>(below.subList(0, k));
        out.addAll(above.subList(0, 3 - k));
        return out;
    }

    /**
     * + / - with two-digit numbers: a carry/borrow slip (±10), a second place-value slip (±20, or ±100
     * in big numbers) - both keep the ones digit - and a counting slip (±1 / ±2). "Only the last
     * digit" leaves three answers, "a rough estimate" leaves the close ones: the child has to work it
     * out. Single digits: one, two, three away. The right answer's place is random ({@link #placed}).
     */
    private List<Integer> additivePool(int correct, boolean multiDigit) {
        if (!multiDigit) return nearPool(correct, 1);
        int[] size = {10, correct >= 200 && random.nextBoolean() ? 100 : 20, rand(1, 2)};
        List<Integer> out = placed(correct, size);
        return out != null ? out : nearPool(correct, 1);
    }

    /**
     * Three wrong answers correct ± size[i]: a random number of them (0-3) below the right answer,
     * among the placements that stay at 0 or above - "pick the middle / biggest / smallest" can't
     * know where the right one is. null when no placement fits.
     */
    private List<Integer> placed(int correct, int[] size) {
        List<Integer> ks = new ArrayList<>(List.of(0, 1, 2, 3));
        Collections.shuffle(ks, random);
        for (int k : ks) {
            List<Integer> idx = new ArrayList<>(List.of(0, 1, 2));
            for (int attempt = 0; attempt < 6; attempt++) {
                Collections.shuffle(idx, random);
                List<Integer> out = new ArrayList<>();
                for (int i = 0; i < 3; i++) out.add(correct + (i < k ? -1 : 1) * size[idx.get(i)]);
                if (out.stream().allMatch(x -> x >= 0 && x != correct) && new LinkedHashSet<>(out).size() == 3) return out;
            }
        }
        return null;
    }

    /**
     * ×: the neighbouring row of the times table (7×8 instead of 7×9: ±a or ±b), a counting slip (±1/±2)
     * and a carry slip (±10) - at different distances, so the right answer is not "the one in the
     * middle" of evenly spread neighbours (the bot's 72% on grade ג).
     */
    private List<Integer> multiplicationPool(int a, int b) {
        int c = a * b;
        int row = random.nextBoolean() ? a : b;
        int[] size = {row, rand(1, 2), row == 10 ? 20 : 10};
        // Two two-digit numbers: "only the last digit" is a real shortcut - two of the mistakes keep it.
        if (a >= 10 && b >= 10) size[1] = row % 10 == 0 ? 30 : 20;
        if (size[0] == size[2] || size[0] == size[1]) size[0] = row + 1 == size[2] ? row + 2 : row + 1;
        List<Integer> out = placed(c, size);
        return out != null ? out : nearPool(c, 1);
    }

    /** ÷ and small answers: one, two or three away. */
    private List<Integer> nearPool(int correct, int unit) {
        List<Integer> pool = new ArrayList<>();
        for (int j = 1; j <= 3; j++) {
            pool.add(correct - unit * j);
            pool.add(correct + unit * j);
        }
        return pool;
    }

    /** Sequences: a step too many / too few, or the step taken wrong by one. */
    private List<Integer> sequencePool(int next, int step) {
        return new ArrayList<>(List.of(next - step, next + step, next + 2 * step, next - 2 * step, next + 1, next - 1));
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
            // 2026-11-12 (curriculum by grade): ג׳ to 1,000, ד׳ to 10,000, larger in ה׳-ו׳ (was 200/1,000/2,000/10,000).
            case G3 -> 1000;
            case G4 -> 10000;
            case G5 -> 20000;
            case G6 -> 100000;
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
        // 2026-11-21 (Shir: "harder when the child is very good - but NOT the next grade's material
        // without the teacher"): harder = the top of the child's own grade, never past its ceiling.
        return Math.max(5, Math.min(base, (int) Math.round(base * stageScale * diffScale)));
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
