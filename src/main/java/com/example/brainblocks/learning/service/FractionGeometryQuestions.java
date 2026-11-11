package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.dto.response.VisualModel;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * שברים (כיתות ה׳-ו׳) וגיאומטריה (כיתה ו׳) - 2026-11-11.
 * Every answer is computed here, never guessed; three wrong answers are the mistakes children
 * really make (adding denominators, forgetting a side, 360 instead of 180...). Questions that need
 * their picture ("איזה חלק צבוע?", "כמה צלעות?") carry a VisualModel the client draws, and are left
 * out of the mixed practice, which has no picture.
 *
 * Stages (1-6) climb in difficulty:
 *  שברים: 1 חלק צבוע · 2 השוואה · 3 שברים שווים · 4 חיבור/חיסור באותו מכנה · 5 שבר מכמות · 6 (ו׳) מכנים שונים / (ה׳) מעורב
 *  גיאומטריה: 1 היקף מלבן · 2 שטח מלבן · 3 ריבוע · 4 זוויות במשולש · 5 שטח משולש ישר-זווית · 6 צורות וצלעות / מעורב
 */
final class FractionGeometryQuestions {

    private final Random random;

    FractionGeometryQuestions(Random random) {
        this.random = random;
    }

    // ---- שברים ----------------------------------------------------------------------------------

    /**
     * 2026-11-11 ("שיהיה מלא ומכל הסוגים"): each level has its new kinds (about 60%) and goes back to
     * everything from the earlier levels (about 40%), so a level is practice, not ten of the same.
     */
    // 2026-11-11 (Shir): no "which fraction is the biggest"; children should know how to WRITE a
    // fraction and how much it is in wholes with a remainder - each its own level.
    private static final int[][] FRACTION_LEVELS = {
            {14, 1},      // 1: לכתוב שבר (שלוש חמישיות = 3/5, ולהפך), איזה חלק צבוע
            {11, 12, 13}, // 2: השבר כמנת חילוק (2 : 3 = 2/3, 5/7 = 5 : 7, קטן/שווה/גדול מ-1) - מחוברת ה׳
            {15, 16},     // 3: כמה שלמים (20/4 = 5), שלמים ושארית (17/5 = 3 שלמים ושארית 2)
            {3, 7},       // 4: שברים שווים, השלמת שבר שווה (1/3 = ?/9)
            {4, 5, 9},    // 5: חיבור/חיסור באותו מכנה, שבר מכמות, בעיה מילולית
            {8},          // 6: צמצום (ו׳: גם עשרוניים ומכנים שונים)
    };

    QuestionResponse fraction(int id, Grade grade, int stage, boolean withPicture) {
        boolean g6 = grade == Grade.G6;
        int s = Math.min(Math.max(stage, 1), 6);
        List<Integer> current = new ArrayList<>();
        for (int k : FRACTION_LEVELS[s - 1]) current.add(k);
        if (s == 6 && g6) {
            current.add(10); // עשרוניים
            current.add(6);  // מכנים שונים
        }
        List<Integer> earlier = new ArrayList<>();
        for (int i = 0; i < s - 1; i++) for (int k : FRACTION_LEVELS[i]) earlier.add(k);
        if (!withPicture) {
            current.remove(Integer.valueOf(1));
            earlier.remove(Integer.valueOf(1));
        }
        int kind = pickKind(current, earlier, 14);
        // 2026-11-11 (Shir: "start with small numbers - it's for learning"): the first questions of a
        // round use small numbers, the later ones grow (moderately).
        small = id < SMALL_FIRST;
        return switch (kind) {
            case 1 -> shaded(id).withSkill("FRAC_SHADED");
            case 2 -> biggest(id, grade).withSkill("FRAC_BIGGEST");
            case 3 -> equivalent(id).withSkill("FRAC_EQUIVALENT");
            case 4 -> sameDenominator(id, grade).withSkill("FRAC_SAME_DENOMINATOR");
            case 5 -> partOf(id, grade).withSkill("FRAC_PART_OF");
            case 7 -> missingInEquivalent(id).withSkill("FRAC_MISSING_EQUIVALENT");
            case 8 -> simplify(id).withSkill("FRAC_SIMPLIFY");
            case 9 -> fractionStory(id, grade).withSkill("FRAC_STORY");
            case 10 -> decimals(id).withSkill("FRAC_DECIMALS");
            case 11 -> divisionAsFraction(id).withSkill("FRAC_AS_DIVISION");
            case 12 -> fractionAsDivision(id).withSkill("FRAC_AS_DIVISION");
            case 13 -> divisionVersusOne(id).withSkill("FRAC_VS_ONE");
            case 14 -> writeFraction(id).withSkill("FRAC_WRITE");
            case 15 -> wholes(id).withSkill("FRAC_WHOLES");
            case 16 -> wholesAndRemainder(id).withSkill("FRAC_WHOLES_REMAINDER");
            default -> differentDenominators(id).withSkill("FRAC_DIFFERENT_DENOMINATORS");
        };
    }

    /** Questions 0..4 of a round: small numbers; from the 6th on: a bit bigger. */
    static final int SMALL_FIRST = 5;
    private boolean small = true;

    /** Upper bound by the round's progress: [smallMax] for the first questions, [max] later. */
    private int upTo(int smallMax, int max) {
        return small ? smallMax : max;
    }

    // ---- לכתוב שבר, שלמים ושארית (2026-11-11, Shir) ----

    private static final String[] PART = {"", "", "חצי", "שליש", "רבע", "חמישית", "שישית", "שביעית", "שמינית", "תשיעית", "עשירית"};
    private static final String[] PARTS = {"", "", "", "שלישים", "רבעים", "חמישיות", "שישיות", "שביעיות", "שמיניות", "תשיעיות", "עשיריות"};
    private static final String[] COUNT_M = {"", "", "שני", "שלושה", "ארבעה", "חמישה", "שישה", "שבעה", "שמונה", "תשעה"};
    private static final String[] COUNT_F = {"", "", "שתי", "שלוש", "ארבע", "חמש", "שש", "שבע", "שמונה", "תשע"};

    /** "שלוש חמישיות" - the way the fraction is said in Hebrew (proper fractions, denominators 2-10). */
    static String fractionWords(int n, int d) {
        if (n == 1) return PART[d];
        boolean masculine = d == 3 || d == 4; // שלישים, רבעים
        return (masculine ? COUNT_M[n] : COUNT_F[n]) + " " + PARTS[d];
    }

    /** "איך כותבים שלוש חמישיות?" -> 3/5, or the other way: "איך קוראים לשבר 3/5?" -> שלוש חמישיות. */
    private QuestionResponse writeFraction(int id) {
        int d = rand(3, upTo(6, 10));
        int n = rand(1, d - 1);
        if (random.nextBoolean()) {
            List<String> wrong = new ArrayList<>(List.of(frac(d, n), frac(n, d + 1), frac(n + 1 < d ? n + 1 : n - 1 == 0 ? n + 2 : n - 1, d)));
            return text(id, Topic.FRACTIONS, "איך כותבים " + fractionWords(n, d) + "?", frac(n, d), wrong,
                    "המילה שבסוף (חמישיות, רבעים...) אומרת לכמה חלקים חילקו - זה המספר שלמטה. המספר שבהתחלה - כמה חלקים לקחו - למעלה.",
                    fractionWords(n, d) + " = " + n + " חלקים מתוך " + d + " = " + frac(n, d) + ".", null);
        }
        List<String> wrong = new ArrayList<>();
        int d2 = d == 10 ? 9 : d + 1;
        wrong.add(fractionWords(n, d2));
        if (n + 1 < d) wrong.add(fractionWords(n + 1, d));
        if (n > 1) wrong.add(fractionWords(n - 1, d));
        wrong.add(fractionWords(Math.max(1, Math.min(n, d - 2)), d - 1 >= 3 ? d - 1 : d + 2));
        return text(id, Topic.FRACTIONS, "איך קוראים לשבר " + frac(n, d) + "?", fractionWords(n, d), wrong,
                "המספר שלמטה אומר לכמה חלקים חילקו (5 - חמישיות), והמספר שלמעלה כמה חלקים לקחו.",
                frac(n, d) + ": " + n + " חלקים מתוך " + d + " - " + fractionWords(n, d) + ".", null);
    }

    /** "כמה שלמים הם 20/4?" - a fraction that is a whole number. */
    private QuestionResponse wholes(int id) {
        int d = rand(2, upTo(5, 10));
        int w = rand(2, upTo(5, 9));
        return text(id, Topic.FRACTIONS, "כמה שלמים הם " + frac(d * w, d) + "?", String.valueOf(w),
                List.of(String.valueOf(d * w - d), String.valueOf(w + 1), String.valueOf(d)),
                "השבר הוא חילוק: מחלקים את המספר שלמעלה במספר שלמטה. כל " + d + " חלקים הם שלם אחד.",
                d * w + " : " + d + " = " + w + ", ולכן " + frac(d * w, d) + " = " + w + " שלמים.", null);
    }

    /** "כמה שלמים יש ב-17/5, וכמה נשאר?" -> 3 שלמים ושארית 2 (written also as 3 ו-2/5). */
    private QuestionResponse wholesAndRemainder(int id) {
        int d = rand(2, upTo(5, 8));
        int w = rand(1, upTo(3, 5));
        int r = rand(1, d - 1);
        int n = w * d + r;
        String correct = answerWR(w, r);
        List<String> wrong = new ArrayList<>();
        wrong.add(answerWR(w + 1, r));                                  // one whole too many
        wrong.add(r + 1 < d ? answerWR(w, r + 1) : answerWR(w, r - 1)); // remainder off by one
        if (w > 1) wrong.add(answerWR(w - 1, r + d));                   // a "remainder" bigger than the bottom
        wrong.add(answerWR(r, w));
        wrong.add(answerWR(w + 2, r));                                  // spare, if two of the above collide
        return text(id, Topic.FRACTIONS, "כמה שלמים יש ב-" + frac(n, d) + ", וכמה נשאר?", correct, wrong,
                "כל " + d + " חלקים הם שלם אחד. בודקים כמה פעמים " + d + " נכנס ב-" + n + ", ומה שנשאר הוא השארית (תמיד קטנה מ-" + d + ").",
                n + " : " + d + " = " + w + " ושארית " + r + ", כלומר " + w + " שלמים ו-" + frac(r, d) + ".", null);
    }

    private static String answerWR(int wholes, int remainder) {
        return (wholes == 1 ? "שלם אחד" : wholes + " שלמים") + " ושארית " + remainder;
    }

    // ---- השבר כמנת חילוק (2026-11-11, from her grade-5 workbook, "נושא 5") ----

    /** "כתבו את התרגיל 2 : 3 כשבר" - the dividend on top, the divisor below (0 : 3, 9 : 9 too). */
    private QuestionResponse divisionAsFraction(int id) {
        int b = rand(2, upTo(6, 10));
        int a = switch (random.nextInt(6)) {
            case 0 -> 0;                       // 0 : 3 = 0/3
            case 1 -> b;                       // 9 : 9 = 9/9
            case 2 -> b * rand(2, upTo(2, 3)); // 20 : 4 = 20/4 (more than 1)
            default -> rand(1, b - 1);
        };
        String correct = frac(a, b);
        List<String> wrong = new ArrayList<>();
        if (a != b && a != 0) wrong.add(frac(b, a)); // upside down - the real mistake
        wrong.add(frac(a, a + b));                   // "out of all of them"
        wrong.add(frac(a + 1, b));
        wrong.add(frac(b, a + b));
        return text(id, Topic.FRACTIONS, "כתבו את התרגיל " + a + " : " + b + " כשבר", correct, wrong,
                "קו השבר הוא סימן החילוק: המספר שמחלקים (הראשון) למעלה, והמספר שמחלקים בו (השני) למטה.",
                a + " : " + b + " = " + correct + " - " + a + " למעלה, " + b + " למטה.", null);
    }

    /** "כתבו את השבר 5/7 כתרגיל חילוק" - top : bottom. */
    private QuestionResponse fractionAsDivision(int id) {
        int d = rand(2, upTo(6, 12));
        int n = random.nextInt(5) == 0 ? 0 : random.nextInt(4) == 0 ? d * rand(1, upTo(2, 3)) : rand(1, d - 1);
        String correct = n + " : " + d;
        List<String> wrong = new ArrayList<>();
        if (n != d && n != 0) wrong.add(d + " : " + n); // reversed
        wrong.add(n + " × " + d);
        wrong.add(n + " : " + (n + d));
        wrong.add(d + " - " + n);
        return text(id, Topic.FRACTIONS, "כתבו את השבר " + frac(n, d) + " כתרגיל חילוק", correct, wrong,
                "קו השבר אומר לחלק: המספר שלמעלה מחולק במספר שלמטה.",
                frac(n, d) + " = " + correct + ".", null);
    }

    /** "התוצאה של 7 : 8 היא..." - smaller than, equal to or bigger than 1 (three options). */
    private QuestionResponse divisionVersusOne(int id) {
        int b = rand(2, upTo(6, 12));
        int kind = random.nextInt(3);
        int a = kind == 0 ? rand(1, b - 1) : kind == 1 ? b : b + rand(1, upTo(b, 2 * b));
        String[] all = {"קטנה מ-1", "שווה ל-1", "גדולה מ-1"};
        return new QuestionResponse(id, Topic.FRACTIONS, "התוצאה של " + a + " : " + b + " היא...",
                new ArrayList<>(List.of(all)), kind,
                "משווים את המספר שמחלקים למספר שמחלקים בו: קטן יותר - התוצאה קטנה מ-1, שווה - בדיוק 1, גדול יותר - יותר מ-1.",
                a + (kind == 0 ? " קטן מ-" : kind == 1 ? " שווה ל-" : " גדול מ-") + b + ", ולכן " + frac(a, b) + " " + all[kind] + ".",
                null);
    }

    /** About 60% from this level's kinds, the rest from earlier ones; [fallback] when both are empty. */
    private int pickKind(List<Integer> current, List<Integer> earlier, int fallback) {
        if (current.isEmpty() && earlier.isEmpty()) return fallback;
        if (earlier.isEmpty() || (!current.isEmpty() && random.nextInt(10) < 6)) return current.get(random.nextInt(current.size()));
        return earlier.get(random.nextInt(earlier.size()));
    }

    /** "1/3 = ?/9" - the number the top was multiplied by is the one the bottom was. */
    private QuestionResponse missingInEquivalent(int id) {
        int[][] bases = {{1, 2}, {1, 3}, {2, 3}, {1, 4}, {3, 4}, {2, 5}, {3, 5}, {1, 6}, {5, 6}};
        int[] b = bases[random.nextInt(bases.length)];
        int k = rand(2, upTo(3, 5));
        int answer = b[0] * k;
        return text(id, Topic.FRACTIONS, "איזה מספר חסר? " + frac(b[0], b[1]) + " = ?/" + b[1] * k, String.valueOf(answer),
                List.of(String.valueOf(b[0] + k), String.valueOf(b[1] * k - b[0]), String.valueOf(answer + b[0])),
                "בכמה הוכפל המספר שלמטה? את המספר שלמעלה כופלים באותו מספר.",
                b[1] + " × " + k + " = " + b[1] * k + ", ולכן " + b[0] + " × " + k + " = " + answer + ".", null);
    }

    /** "צמצמו את 6/8" - divide top and bottom by the same number, as far as it goes. */
    private QuestionResponse simplify(int id) {
        int[][] bases = {{1, 2}, {1, 3}, {2, 3}, {1, 4}, {3, 4}, {2, 5}, {3, 5}, {4, 5}, {5, 6}, {3, 7}};
        int[] b = bases[random.nextInt(bases.length)];
        int k = rand(2, upTo(3, 5));
        String correct = frac(b[0], b[1]);
        int g = gcd(b[0] * k, b[1] * k);
        // Half-way (k = 4 -> divided by 2 only) is the real mistake; the others are near misses.
        List<String> wrong = List.of(k == 4 ? frac(b[0] * 2, b[1] * 2) : frac(b[0] + 1, b[1] + 1), frac(b[0], b[1] + 1), frac(b[0] + 1, b[1]));
        return text(id, Topic.FRACTIONS, "צמצמו עד הסוף את " + frac(b[0] * k, b[1] * k), correct, wrong,
                "מחלקים את המספר שלמעלה ואת המספר שלמטה באותו מספר, עד שאין עוד מספר שמחלק את שניהם.",
                frac(b[0] * k, b[1] * k) + " = " + correct + " (חילקנו ב-" + g + ").", null);
    }

    /** A story: "לנועה 12 סוכריות, היא נתנה רבע לחברה. כמה סוכריות נתנה?" */
    private QuestionResponse fractionStory(int id, Grade grade) {
        String[][] parts = {{"חצי", "2"}, {"שליש", "3"}, {"רבע", "4"}, {"חמישית", "5"}};
        String[] p = parts[random.nextInt(grade == Grade.G6 ? 4 : 3)];
        int d = Integer.parseInt(p[1]);
        int whole = d * rand(2, upTo(4, 9));
        String[] names = {"נועה", "דניאל", "מאיה", "יואב", "תמר", "איתי"};
        String[] things = {"סוכריות", "מדבקות", "קלפים", "גולות", "עפרונות"};
        String name = names[random.nextInt(names.length)];
        String thing = things[random.nextInt(things.length)];
        boolean left = random.nextBoolean();
        int gave = whole / d;
        int r = left ? whole - gave : gave;
        return text(id, Topic.FRACTIONS, "ל" + name + " יש " + whole + " " + thing + ". " + p[0] + " מהן נתנו לחבר. "
                        + (left ? "כמה נשארו?" : "כמה נתנו?"), String.valueOf(r),
                List.of(String.valueOf(left ? gave : whole - gave), String.valueOf(whole - d), String.valueOf(r + d)),
                p[0] + " זה לחלק ל-" + d + " חלקים שווים ולקחת חלק אחד." + (left ? " אחר כך בודקים כמה נשאר." : ""),
                p[0] + " מ-" + whole + " זה " + gave + (left ? ", ונשארו " + r : "") + ".", null);
    }

    /** Grade 6: "0.3 + 0.4 = ?" / "0.5 כשבר" - tenths only, written with a point. */
    private QuestionResponse decimals(int id) {
        if (random.nextBoolean()) {
            int a = rand(1, 7), b = rand(1, 9 - a);
            String correct = "0." + (a + b);
            return text(id, Topic.FRACTIONS, "0." + a + " + 0." + b + " = ?", correct,
                    List.of("0." + a + b, "0." + (a + b + 1), (a + b) + ""),
                    "0.3 זה 3 עשיריות. מחברים עשיריות כמו שמחברים מספרים רגילים.",
                    a + " עשיריות ועוד " + b + " עשיריות זה " + (a + b) + " עשיריות: " + correct + ".", null);
        }
        int[][] pairs = {{1, 2, 5}, {1, 5, 2}, {2, 5, 4}, {3, 10, 3}, {7, 10, 7}, {4, 5, 8}};
        int[] p = pairs[random.nextInt(pairs.length)];
        String correct = "0." + p[2];
        return text(id, Topic.FRACTIONS, "איך כותבים את " + frac(p[0], p[1]) + " כמספר עשרוני?", correct,
                List.of("0." + p[0], "0." + p[0] + p[1], p[2] == 5 ? "0.2" : "0.5"),
                "מחפשים שבר שווה עם 10 למטה - ואז המספר שלמעלה הוא מספר העשיריות.",
                frac(p[0], p[1]) + " = " + frac(p[2], 10) + " = " + correct + ".", null);
    }

    /** "איזה חלק מהעיגול צבוע?" - picture: a pie or bar with n of d parts coloured. */
    private QuestionResponse shaded(int id) {
        int d = rand(2, upTo(6, 8));
        int n = rand(1, d - 1);
        String correct = frac(n, d);
        List<String> wrong = List.of(frac(d - n, d), frac(n, d + 1), n == 1 ? frac(d, n + d) : frac(n - 1, d), frac(d, n));
        boolean pie = random.nextBoolean();
        return text(id, Topic.FRACTIONS, "איזה חלק " + (pie ? "מהעיגול" : "מהמלבן") + " צבוע?", correct, wrong,
                "סופרים לכמה חלקים שווים חולקה הצורה - זה המספר למטה. אחר כך סופרים כמה חלקים צבועים - זה המספר למעלה.",
                n + " חלקים צבועים מתוך " + d + ", כלומר " + correct + ".",
                new VisualModel(pie ? "FRACTION_PIE" : "FRACTION_BAR", null, n, d, 0, null));
    }

    /** Same denominator (more parts = bigger) or, from grade 6, same numerator (smaller parts). */
    private QuestionResponse biggest(int id, Grade grade) {
        boolean sameNumerator = grade == Grade.G6 && random.nextBoolean();
        List<String> options = new ArrayList<>();
        String correct;
        if (sameNumerator) {
            int n = rand(1, 3);
            List<Integer> dens = distinct(4, n + 1, 12);
            int smallest = Collections.min(dens);
            dens.forEach(x -> options.add(frac(n, x)));
            correct = frac(n, smallest);
        } else {
            int d = rand(5, 12);
            List<Integer> nums = distinct(4, 1, d - 1);
            int largest = Collections.max(nums);
            nums.forEach(x -> options.add(frac(x, d)));
            correct = frac(largest, d);
        }
        options.remove(correct);
        return text(id, Topic.FRACTIONS, "איזה שבר הכי גדול?", correct, options,
                sameNumerator ? "כשהמספר למעלה שווה: מחלקים לפחות חלקים - כל חלק גדול יותר."
                        : "כשהמספר למטה שווה: החלקים באותו גודל, אז יותר חלקים זה יותר.",
                correct + " הוא הגדול ביותר.", null);
    }

    /** "איזה שבר שווה ל-1/2?" - multiply top and bottom by the same number. */
    private QuestionResponse equivalent(int id) {
        int[][] bases = {{1, 2}, {1, 3}, {2, 3}, {1, 4}, {3, 4}, {2, 5}};
        int[] b = bases[random.nextInt(bases.length)];
        int k = rand(2, upTo(3, 4));
        String correct = frac(b[0] * k, b[1] * k);
        List<String> wrong = List.of(frac(b[0] * k, b[1] * k + 1), frac(b[0] + k, b[1] + k), frac(b[0] * k + 1, b[1] * k));
        return text(id, Topic.FRACTIONS, "איזה שבר שווה ל-" + frac(b[0], b[1]) + "?", correct, wrong,
                "שבר לא משתנה כשכופלים את המספר למעלה ואת המספר למטה באותו מספר.",
                frac(b[0], b[1]) + " = " + correct + " (כפול " + k + " למעלה ולמטה).", null);
    }

    /** "2/7 + 3/7 = ?" - only the numerators change. The classic mistake (adding the bottoms) is an option. */
    private QuestionResponse sameDenominator(int id, Grade grade) {
        int d = rand(5, upTo(8, grade == Grade.G6 ? 12 : 10));
        boolean plus = random.nextBoolean();
        int a, b, r;
        if (plus) {
            a = rand(1, d - 2);
            b = rand(1, d - 1 - a);
            r = a + b;
        } else {
            a = rand(2, d - 1);
            b = rand(1, a - 1);
            r = a - b;
        }
        String correct = frac(r, d);
        // Real mistakes: adding the bottoms too (plus), or adding instead of subtracting (minus).
        List<String> wrong = List.of(plus ? frac(r, 2 * d) : frac(a + b, d), frac(r + 1, d), frac(r > 1 ? r - 1 : r + 2, d));
        return text(id, Topic.FRACTIONS, frac(a, d) + (plus ? " + " : " - ") + frac(b, d) + " = ?", correct, wrong,
                "כשהמספר למטה שווה, " + (plus ? "מחברים" : "מחסרים") + " רק את המספרים למעלה. המספר למטה נשאר.",
                a + (plus ? " + " : " - ") + b + " = " + r + ", והמכנה נשאר " + d + ".", null);
    }

    /** "כמה זה 3/4 מ-20?" - divide by the bottom, multiply by the top (a whole-number answer). */
    private QuestionResponse partOf(int id, Grade grade) {
        int d = new int[] {2, 3, 4, 5, 10}[random.nextInt(5)];
        int n = grade == Grade.G6 ? rand(1, d - 1) : (random.nextBoolean() ? 1 : rand(1, d - 1));
        int whole = d * rand(2, upTo(5, grade == Grade.G6 ? 12 : 8));
        int r = whole / d * n;
        List<String> wrong = new ArrayList<>(List.of(String.valueOf(whole / d), String.valueOf(whole - r), String.valueOf(r + whole / d)));
        wrong.remove(String.valueOf(r));
        return text(id, Topic.FRACTIONS, "כמה זה " + frac(n, d) + " מ-" + whole + "?", String.valueOf(r), wrong,
                "מחלקים את " + whole + " ל-" + d + " חלקים שווים, ולוקחים " + n + " מהם.",
                whole + " ÷ " + d + " = " + whole / d + ", ועוד " + n + " חלקים כאלה זה " + r + ".", null);
    }

    /** Grade 6: "1/2 + 1/4 = ?" - one denominator divides the other; the answer is simplified. */
    private QuestionResponse differentDenominators(int id) {
        int small = new int[] {2, 3, 4, 5}[random.nextInt(4)];
        int big = small * rand(2, 3);
        int a = rand(1, small - 1);
        int b = rand(1, big - 1);
        int top = a * (big / small) + b;
        int bottom = big;
        if (top >= bottom) { // keep it below 1
            b = Math.max(1, bottom - a * (big / small) - 1);
            top = a * (big / small) + b;
        }
        int g = gcd(top, bottom);
        String correct = frac(top / g, bottom / g);
        List<String> wrong = List.of(frac(a + b, small + big), frac(a + b, big), frac(top + 1, bottom));
        return text(id, Topic.FRACTIONS, frac(a, small) + " + " + frac(b, big) + " = ?", correct, wrong,
                "קודם מביאים למכנה משותף: הופכים את " + frac(a, small) + " לשבר עם " + big + " למטה.",
                frac(a, small) + " = " + frac(a * (big / small), big) + ", ואז " + frac(a * (big / small), big) + " + " + frac(b, big)
                        + " = " + frac(top, bottom) + (g > 1 ? " = " + correct : "") + ".", null);
    }

    // ---- גיאומטריה ------------------------------------------------------------------------------

    private static final int[][] GEOMETRY_LEVELS = {
            {1, 7},     // 1: היקף מלבן, היקף משולש
            {2, 8},     // 2: שטח מלבן, צלע חסרה לפי היקף
            {3, 9},     // 3: ריבוע, צלע ריבוע לפי שטח
            {4, 10, 11}, // 4: זוויות במשולש, זווית על קו ישר, משולש ישר-זווית
            {5},        // 5: שטח משולש ישר-זווית
            {6, 12},    // 6: כמה צלעות, שמות צורות
    };

    /**
     * 2026-11-11 (Shir: "angles one track, sides another"): geometry split into three tracks, each with
     * its own six levels (60% new / 40% earlier, like every topic). The old mixed GEOMETRY stays for
     * the mixed practice.
     *  זוויות:       1 סוג זווית · 2 כמה מעלות (ישרה/שטוחה/סיבוב) · 3 איזו זווית חדה/קהה · 4 סוג משולש · 5-6 מעורב
     *  חישוב זוויות: 1 קו ישר · 2 משולש · 3 ישר-זווית · 4 סביב נקודה · 5 שווה-שוקיים · 6 מעורב
     *  צלעות והיקף: 1 צלעות ושמות · 2 היקף מלבן · 3 היקף משולש · 4 היקף ריבוע · 5 צלע חסרה · 6 מעורב
     *  שטח:         1 מלבן · 2 ריבוע · 3 צלע לפי שטח · 4 משולש ישר-זווית · 5 שטח מהיקף · 6 מעורב
     */
    private static final java.util.Map<Topic, int[][]> TRACK_LEVELS = java.util.Map.of(
            // 2026-11-11 (Shir): "חישוב זוויות" separate - זוויות = recognizing, ANGLE_CALC = calculating.
            Topic.ANGLES, new int[][] {{13}, {18}, {19}, {20}, {13, 18, 19, 20}, {}},
            Topic.ANGLE_CALC, new int[][] {{10}, {4}, {11}, {14}, {21}, {}},
            Topic.PERIMETER, new int[][] {{6, 12}, {1}, {7}, {15}, {8}, {}},
            Topic.AREA, new int[][] {{2}, {16}, {9}, {5}, {17}, {}});

    /** Which topic the questions being built belong to (the geometry methods are shared by the tracks). */
    private Topic geoTopic = Topic.GEOMETRY;

    QuestionResponse geometryTrack(int id, Topic track, int stage, boolean withPicture) {
        small = id < SMALL_FIRST; // first questions of a round: small numbers
        int[][] levels = TRACK_LEVELS.get(track);
        int s = Math.min(Math.max(stage, 1), 6);
        List<Integer> current = new ArrayList<>();
        for (int k : levels[s - 1]) current.add(k);
        List<Integer> earlier = new ArrayList<>();
        for (int i = 0; i < s - 1; i++) for (int k : levels[i]) earlier.add(k);
        if (!withPicture) {
            current.remove(Integer.valueOf(6));
            earlier.remove(Integer.valueOf(6));
        }
        geoTopic = track;
        try {
            return geometryKind(id, pickKind(current, earlier, track == Topic.PERIMETER ? 12 : levels[0][0]));
        } finally {
            geoTopic = Topic.GEOMETRY;
        }
    }

    QuestionResponse geometry(int id, int stage, boolean withPicture) {
        small = id < SMALL_FIRST; // first questions of a round: small numbers
        int s = Math.min(Math.max(stage, 1), 6);
        List<Integer> current = new ArrayList<>();
        for (int k : GEOMETRY_LEVELS[s - 1]) current.add(k);
        List<Integer> earlier = new ArrayList<>();
        for (int i = 0; i < s - 1; i++) for (int k : GEOMETRY_LEVELS[i]) earlier.add(k);
        if (!withPicture) current.remove(Integer.valueOf(6));
        return geometryKind(id, pickKind(current, earlier, 1));
    }

    private QuestionResponse geometryKind(int id, int kind) {
        return switch (kind) {
            case 1 -> perimeter(id).withSkill("PER_RECTANGLE");
            case 2 -> area(id).withSkill("AREA_RECTANGLE");
            case 3 -> square(id).withSkill("GEO_SQUARE");
            case 4 -> angles(id).withSkill("ANG_TRIANGLE");
            case 5 -> triangleArea(id).withSkill("AREA_RIGHT_TRIANGLE");
            case 6 -> sides(id).withSkill("GEO_SIDES");
            case 7 -> trianglePerimeter(id).withSkill("PER_TRIANGLE");
            case 8 -> missingSide(id).withSkill("PER_MISSING_SIDE");
            case 9 -> squareSideFromArea(id).withSkill("AREA_SIDE_FROM_AREA");
            case 10 -> straightAngle(id).withSkill("ANG_STRAIGHT_LINE");
            case 11 -> rightTriangleAngle(id).withSkill("ANG_RIGHT_TRIANGLE");
            case 13 -> angleType(id).withSkill("ANG_TYPE");
            case 14 -> aroundPoint(id).withSkill("ANG_AROUND_POINT");
            case 15 -> squarePerimeter(id).withSkill("PER_SQUARE");
            case 16 -> squareArea(id).withSkill("AREA_SQUARE");
            case 17 -> areaFromPerimeter(id).withSkill("AREA_FROM_PERIMETER");
            case 18 -> namedAngleDegrees(id).withSkill("ANG_NAMED_DEGREES");
            case 19 -> whichAngleIs(id).withSkill("ANG_WHICH");
            case 20 -> triangleType(id).withSkill("ANG_TRIANGLE_TYPE");
            case 21 -> isoscelesAngles(id).withSkill("ANG_ISOSCELES");
            default -> shapeName(id).withSkill("GEO_SHAPE_NAME");
        };
    }

    /** "זווית של 120° היא זווית..." - חדה / ישרה / קהה / שטוחה. */
    private QuestionResponse angleType(int id) {
        int kind = random.nextInt(4);
        int a = switch (kind) {
            case 0 -> 5 * rand(2, 17);  // 10-85
            case 1 -> 90;
            case 2 -> 5 * rand(19, 35); // 95-175
            default -> 180;
        };
        String[] names = {"חדה", "ישרה", "קהה", "שטוחה"};
        return new QuestionResponse(id, geoTopic, "זווית של " + a + "° היא זווית...", new ArrayList<>(List.of(names)), kind,
                "משווים ל-90°: פחות מ-90 - חדה, בדיוק 90 - ישרה, בין 90 ל-180 - קהה, 180 - שטוחה (קו ישר).",
                a + "° " + (kind == 0 ? "זה פחות מ-90°" : kind == 1 ? "זה בדיוק 90°" : kind == 2 ? "זה בין 90° ל-180°" : "זה קו ישר")
                        + ", ולכן היא " + names[kind] + ".", null);
    }

    /** "כמה מעלות יש בזווית ישרה?" - 90 / 180 / 360. */
    private QuestionResponse namedAngleDegrees(int id) {
        String[][] named = {{"בזווית ישרה", "90"}, {"בזווית שטוחה", "180"}, {"בסיבוב שלם", "360"}, {"בחצי סיבוב", "180"}, {"ברבע סיבוב", "90"}};
        String[] q = named[random.nextInt(named.length)];
        int r = Integer.parseInt(q[1]);
        return number(id, "כמה מעלות יש " + q[0] + "?", r, List.of(90, 180, 360, 45).stream().filter(x -> x != r).toList(),
                "זווית ישרה = פינה של דף (90°). שתי ישרות = קו ישר, זווית שטוחה (180°). ארבע ישרות = סיבוב שלם (360°).",
                q[0].substring(1) + " יש " + r + "°.", null);
    }

    /** "איזו מהזוויות קהה?" - pick the one of the right type among four sizes. */
    private QuestionResponse whichAngleIs(int id) {
        int kind = random.nextInt(2); // 0 = חדה, 1 = קהה
        int acute = 5 * rand(2, 17), obtuse = 5 * rand(19, 35);
        int correct = kind == 0 ? acute : obtuse;
        List<Integer> wrong = kind == 0
                ? List.of(90, obtuse, 5 * rand(19, 35) == obtuse ? 180 : obtuse + 5 > 175 ? 180 : obtuse + 5)
                : List.of(90, acute, 180);
        List<String> w = new ArrayList<>();
        for (int x : wrong) w.add(x + "°");
        return text(id, geoTopic, "איזו מהזוויות היא זווית " + (kind == 0 ? "חדה" : "קהה") + "?", correct + "°", w,
                kind == 0 ? "זווית חדה קטנה מ-90° (צרה יותר מפינה של דף)." : "זווית קהה גדולה מ-90° אבל קטנה מ-180°.",
                correct + "° " + (kind == 0 ? "קטנה מ-90°" : "בין 90° ל-180°") + ", ולכן היא " + (kind == 0 ? "חדה" : "קהה") + ".", null);
    }

    /** "במשולש יש זווית של 120°. זה משולש..." - קהה-זווית / ישר-זווית / חד-זווית. */
    private QuestionResponse triangleType(int id) {
        int kind = random.nextInt(3);
        int a, b;
        if (kind == 0) {          // all acute
            a = 5 * rand(9, 16);  // 45-80
            b = 5 * rand(Math.max(9, 19 - a / 5), 16);
        } else if (kind == 1) {   // right
            a = 90;
            b = 5 * rand(4, 13);
        } else {                  // obtuse
            a = 5 * rand(19, 28); // 95-140
            b = 5 * rand(2, Math.max(2, (170 - a) / 5));
        }
        int c = 180 - a - b;
        String[] names = {"חד-זווית", "ישר-זווית", "קהה-זווית"};
        return new QuestionResponse(id, geoTopic, "למשולש יש זוויות של " + a + "°, " + b + "° ו-" + c + "°. זה משולש...",
                new ArrayList<>(List.of(names)), kind,
                "מסתכלים על הזווית הגדולה: אם יש זווית של 90° - ישר-זווית, אם יש זווית גדולה מ-90° - קהה-זווית, ואם כולן קטנות מ-90° - חד-זווית.",
                "הזווית הגדולה היא " + Math.max(a, Math.max(b, c)) + "°, ולכן זה משולש " + names[kind] + ".", null);
    }

    /** Isosceles: the two base angles are equal - from the top angle, or the other way. */
    private QuestionResponse isoscelesAngles(int id) {
        if (random.nextBoolean()) {
            int top = 10 * rand(2, upTo(10, 14)); // 20-100/140
            int r = (180 - top) / 2;
            return number(id, "במשולש שווה-שוקיים זווית הראש היא " + top + "°. כמה מעלות כל אחת מזוויות הבסיס?", r,
                    List.of(180 - top, 90 - top / 2 + 10, top), "שתי זוויות הבסיס שוות. מ-180° מורידים את זווית הראש, ומחלקים את מה שנשאר ל-2.",
                    "180 - " + top + " = " + (180 - top) + ", ולחלק ל-2: " + r + "°.", null);
        }
        int baseAngle = 5 * rand(6, 17); // 30-85
        int r = 180 - 2 * baseAngle;
        return number(id, "במשולש שווה-שוקיים כל זווית בסיס היא " + baseAngle + "°. כמה מעלות זווית הראש?", r,
                List.of(180 - baseAngle, baseAngle, r + 10), "שתי זוויות הבסיס שוות - יחד הן פעמיים " + baseAngle + "°. מה שחסר עד 180° הוא זווית הראש.",
                "2 × " + baseAngle + " = " + 2 * baseAngle + ", ו-180 - " + 2 * baseAngle + " = " + r + "°.", null);
    }

    /** Three angles around a point make 360°. */
    private QuestionResponse aroundPoint(int id) {
        int a = 10 * rand(5, 15), b = 10 * rand(4, 33 - a / 10 - 2);
        int r = 360 - a - b;
        return number(id, "סביב נקודה יש שלוש זוויות. שתיים מהן " + a + "° ו-" + b + "°. כמה מעלות השלישית?", r,
                List.of(180 - a - b > 0 ? 180 - a - b : r + 20, a + b, r - 10),
                "כל הזוויות סביב נקודה יחד הן סיבוב שלם - 360°.",
                "360 - " + a + " - " + b + " = " + r + "°.", null);
    }

    private QuestionResponse squarePerimeter(int id) {
        int s = rand(2, upTo(6, 15));
        return number(id, "מה ההיקף של ריבוע שצלעו " + s + " ס\"מ?", 4 * s,
                balanced(4 * s, List.of(2 * s, 3 * s, 4 * s - 2, s * s), List.of(s * s, 5 * s, 4 * s + 2, 4 * s + 4)), "לריבוע 4 צלעות שוות - היקף = 4 × צלע.",
                "4 × " + s + " = " + 4 * s + " ס\"מ.", new VisualModel("SQUARE", null, s, s, 0, null));
    }

    private QuestionResponse squareArea(int id) {
        int s = rand(2, upTo(6, 12));
        return number(id, "מה השטח של ריבוע שצלעו " + s + " ס\"מ? (בסמ\"ר)", s * s,
                List.of(4 * s == s * s ? s * s + s : 4 * s, 2 * s, s * s + s), "שטח ריבוע = צלע × צלע.",
                s + " × " + s + " = " + s * s + " סמ\"ר.", new VisualModel("SQUARE", null, s, s, 0, null));
    }

    /** "ההיקף של מלבן 26 ס"מ ואורכו 8. מה השטח?" - first the width, then length × width. */
    private QuestionResponse areaFromPerimeter(int id) {
        int w = rand(4, upTo(7, 12)), h = rand(2, upTo(5, 9));
        if (w == h) w++;
        int p = 2 * (w + h);
        return number(id, "ההיקף של מלבן הוא " + p + " ס\"מ ואורכו " + w + " ס\"מ. מה השטח שלו? (בסמ\"ר)", w * h,
                List.of(p, w * (p / 2), p - w), "קודם מוצאים את הרוחב (חצי ההיקף פחות האורך), ואז אורך × רוחב.",
                "רוחב: " + p + " ÷ 2 - " + w + " = " + h + ". שטח: " + w + " × " + h + " = " + w * h + " סמ\"ר.", null);
    }

    private QuestionResponse trianglePerimeter(int id) {
        int a = rand(3, upTo(8, 12)), b = rand(3, upTo(8, 12)), c = rand(Math.abs(a - b) + 1, a + b - 1);
        int r = a + b + c;
        return number(id, "למשולש יש צלעות באורך " + a + ", " + b + " ו-" + c + " ס\"מ. מה ההיקף שלו?", r,
                List.of(a + b, a * b, r + 1), "היקף = מחברים את כל הצלעות מסביב. למשולש יש 3 צלעות.",
                a + " + " + b + " + " + c + " = " + r + " ס\"מ.", new VisualModel("TRIANGLE_SIDES", null, a, b, c, null));
    }

    /** "היקף מלבן 30 ס"מ, אורכו 9. מה רוחבו?" - half the perimeter, minus the length. */
    private QuestionResponse missingSide(int id) {
        int w = rand(4, upTo(9, 15)), h = rand(2, upTo(6, 12));
        if (w == h) w++;
        int p = 2 * (w + h);
        return number(id, "ההיקף של מלבן הוא " + p + " ס\"מ, ואורכו " + w + " ס\"מ. מה הרוחב שלו?", h,
                // 2026-11-11: all three were bigger than the answer - "pick the smallest" got 67%.
                List.of(h > 1 ? h - 1 : p / 2, 2 * h == p / 2 ? h + 1 : 2 * h, p / 2), "אורך ורוחב יחד הם חצי מההיקף. מחצי ההיקף מורידים את האורך.",
                p + " ÷ 2 = " + (w + h) + ", ו-" + (w + h) + " - " + w + " = " + h + " ס\"מ.", null);
    }

    private QuestionResponse squareSideFromArea(int id) {
        int s = rand(2, upTo(6, 12));
        return number(id, "השטח של ריבוע הוא " + s * s + " סמ\"ר. מה אורך הצלע שלו?", s,
                List.of(s * s / 4 == s ? s + 2 : s * s / 4, s * s / 2, s + 1), "איזה מספר כפול עצמו נותן את השטח?",
                s + " × " + s + " = " + s * s + ", אז הצלע היא " + s + " ס\"מ.", null);
    }

    /** Two angles on a straight line make 180°. */
    private QuestionResponse straightAngle(int id) {
        int a = 5 * rand(4, 32);
        int r = 180 - a;
        return number(id, "על קו ישר יש שתי זוויות צמודות. אחת מהן " + a + "°. כמה מעלות הזווית השנייה?", r,
                List.of(360 - a, Math.abs(90 - a) == r ? r + 10 : Math.abs(90 - a), a),
                "זוויות על קו ישר יחד הן 180°.", "180 - " + a + " = " + r + "°.", new VisualModel("LINE_ANGLE", null, a, 0, 0, null));
    }

    private QuestionResponse rightTriangleAngle(int id) {
        int a = rand(15, 75);
        int r = 90 - a;
        return number(id, "במשולש ישר-זווית אחת הזוויות החדות היא " + a + "°. כמה מעלות הזווית החדה השנייה?", r,
                List.of(180 - a, 180 - 90 - a + 10, a), "במשולש ישר-זווית יש זווית של 90°, ושתי האחרות יחד הן 90°.",
                "90 - " + a + " = " + r + "°.", null);
    }

    private QuestionResponse shapeName(int id) {
        int n = new int[] {3, 4, 5, 6, 8}[random.nextInt(5)];
        String[] names = {"", "", "", "משולש", "מרובע", "מחומש", "משושה", "", "מתומן"};
        List<String> wrong = new ArrayList<>();
        for (int m : new int[] {3, 4, 5, 6, 8}) if (m != n) wrong.add(names[m]);
        Collections.shuffle(wrong, random);
        return text(id, geoTopic, "איך קוראים לצורה עם " + n + " צלעות?", names[n], wrong.subList(0, 3),
                "שמות הצורות באים ממספרים: מחומש - חמש, משושה - שש, מתומן - שמונה.", "לצורה עם " + n + " צלעות קוראים " + names[n] + ".", null);
    }

    private QuestionResponse perimeter(int id) {
        int w = rand(3, upTo(8, 15)), h = rand(2, upTo(6, 12));
        if (w == h) w++;
        int r = 2 * (w + h);
        return number(id, "מה ההיקף של מלבן שאורכו " + w + " ס\"מ ורוחבו " + h + " ס\"מ?", r,
                // 2026-11-11: the right answer's place among the mistakes is random ("pick the middle" got 52%).
                balanced(r, List.of(w + h, 2 * w + h, w + 2 * h, r - 2), List.of(w * h, r + w, r + h, r + 2)),
                "היקף = כל הצלעות מסביב. במלבן יש 2 אורכים ו-2 רוחבים.",
                w + " + " + h + " + " + w + " + " + h + " = " + r + " ס\"מ.", new VisualModel("RECT", null, w, h, 0, null));
    }

    private QuestionResponse area(int id) {
        int w = rand(3, upTo(7, 12)), h = rand(2, upTo(5, 9));
        if (w == h) w++;
        int r = w * h;
        return number(id, "מה השטח של מלבן שאורכו " + w + " ס\"מ ורוחבו " + h + " ס\"מ? (בסמ\"ר)", r,
                List.of(2 * (w + h), w + h, r + w),
                "שטח מלבן = אורך × רוחב (כמה ריבועים של 1 ס\"מ נכנסים בתוכו).",
                w + " × " + h + " = " + r + " סמ\"ר.", new VisualModel("RECT", null, w, h, 0, null));
    }

    private QuestionResponse square(int id) {
        int s = rand(2, upTo(6, 12));
        boolean askArea = random.nextBoolean();
        int r = askArea ? s * s : 4 * s;
        return number(id, (askArea ? "מה השטח" : "מה ההיקף") + " של ריבוע שצלעו " + s + " ס\"מ?" + (askArea ? " (בסמ\"ר)" : ""), r,
                askArea ? List.of(4 * s, 2 * s, s * s + s) : List.of(s * s == 4 * s ? s * s + 1 : s * s, 2 * s, 3 * s),
                askArea ? "שטח ריבוע = צלע × צלע." : "לריבוע 4 צלעות שוות - היקף = 4 × צלע.",
                askArea ? s + " × " + s + " = " + r + " סמ\"ר." : "4 × " + s + " = " + r + " ס\"מ.",
                new VisualModel("SQUARE", null, s, s, 0, null));
    }

    private QuestionResponse angles(int id) {
        int a = rand(30, 100), b = rand(20, 150 - a);
        int r = 180 - a - b;
        return number(id, "במשולש יש זווית של " + a + "° וזווית של " + b + "°. כמה מעלות הזווית השלישית?", r,
                List.of(360 - a - b, 90 - Math.min(a, b) > 0 && 90 - Math.min(a, b) != r ? 90 - Math.min(a, b) : r + 10, a + b),
                "סכום שלוש הזוויות במשולש הוא תמיד 180°.",
                "180 - " + a + " - " + b + " = " + r + "°.", new VisualModel("TRIANGLE", null, a, b, 0, null));
    }

    private QuestionResponse triangleArea(int id) {
        int base = 2 * rand(2, upTo(4, 8)), height = rand(2, upTo(6, 10));
        int r = base * height / 2;
        return number(id, "מה השטח של משולש ישר-זווית שבסיסו " + base + " ס\"מ וגובהו " + height + " ס\"מ? (בסמ\"ר)", r,
                List.of(base * height, base + height, r + base),
                "משולש ישר-זווית הוא חצי ממלבן: בסיס × גובה, ואז חצי.",
                base + " × " + height + " = " + base * height + ", וחצי מזה " + r + " סמ\"ר.",
                new VisualModel("RIGHT_TRIANGLE", null, base, height, 0, null));
    }

    /** "כמה צלעות יש לצורה?" with its picture; names stay out so the child counts. */
    private QuestionResponse sides(int id) {
        int n = new int[] {3, 4, 5, 6, 8}[random.nextInt(5)];
        String[] names = {"", "", "", "משולש", "מרובע", "מחומש", "משושה", "", "מתומן"};
        return number(id, "כמה צלעות יש לצורה?", n, balanced(n, List.of(n - 1, n - 2), List.of(n + 1, n + 2, n + 3)),
                "צלע היא קו ישר אחד בשפת הצורה. סופרים אותן מסביב.",
                "יש " + n + " צלעות - זה " + names[n] + ".", new VisualModel("POLYGON", null, n, 0, 0, null));
    }

    // ---- helpers --------------------------------------------------------------------------------

    /**
     * Three wrong answers - real mistakes from [below] (smaller than the right one) and [above] - with a
     * random number of them below, so the right answer's place (smallest ... biggest) gives nothing
     * away (2026-11-11, the "sneaky child" audit).
     */
    private List<Integer> balanced(int correct, List<Integer> below, List<Integer> above) {
        List<Integer> lo = new ArrayList<>(new LinkedHashSet<>(below.stream().filter(x -> x >= 0 && x < correct).toList()));
        List<Integer> hi = new ArrayList<>(new LinkedHashSet<>(above.stream().filter(x -> x > correct).toList()));
        Collections.shuffle(lo, random);
        Collections.shuffle(hi, random);
        int min = Math.max(0, 3 - hi.size()), max = Math.min(3, lo.size());
        int k = max < min ? lo.size() : min + random.nextInt(max - min + 1);
        List<Integer> out = new ArrayList<>(lo.subList(0, Math.min(k, lo.size())));
        out.addAll(hi.subList(0, Math.min(3 - out.size(), hi.size())));
        return out;
    }

    static String frac(int n, int d) {
        return n + "/" + d;
    }

    static int gcd(int a, int b) {
        return b == 0 ? Math.abs(a) : gcd(b, a % b);
    }

    private int rand(int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private List<Integer> distinct(int count, int min, int max) {
        Set<Integer> s = new LinkedHashSet<>();
        int guard = 0;
        while (s.size() < count && guard++ < 500) s.add(rand(min, max));
        for (int x = min; s.size() < count; x++) s.add(x);
        return new ArrayList<>(s);
    }

    private QuestionResponse number(int id, String text, int correct, List<Integer> wrong, String hint, String explanation,
                                    VisualModel visual) {
        List<String> w = new ArrayList<>();
        for (int x : wrong) w.add(String.valueOf(Math.max(0, x)));
        return text(id, geoTopic, text, String.valueOf(correct), w, hint, explanation, visual);
    }

    /** Four distinct options: the correct one + three wrong ones (topped up if some collide). */
    private QuestionResponse text(int id, Topic topic, String text, String correct, List<String> wrong,
                                  String hint, String explanation, VisualModel visual) {
        Set<String> options = new LinkedHashSet<>();
        options.add(correct);
        for (String w : wrong) {
            if (options.size() < 4 && w != null && !w.isBlank() && !w.equals(correct) && !w.contains("/0") && !w.startsWith("0/")) {
                options.add(w);
            }
        }
        int bump = 1;
        while (options.size() < 4) {
            options.add(correct.contains("/") ? (Integer.parseInt(correct.split("/")[0]) + bump) + "/" + correct.split("/")[1]
                    : correct.contains(".") ? correct + bump
                    : correct.matches("\\d+") ? String.valueOf(Integer.parseInt(correct) + bump) : correct + " " + bump);
            bump++;
        }
        List<String> list = new ArrayList<>(options);
        Collections.shuffle(list, random);
        return new QuestionResponse(id, topic, text, list, list.indexOf(correct), hint, explanation, visual);
    }
}
