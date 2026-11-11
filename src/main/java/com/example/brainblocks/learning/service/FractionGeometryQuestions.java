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

    QuestionResponse fraction(int id, Grade grade, int stage, boolean withPicture) {
        boolean g6 = grade == Grade.G6;
        int kind = switch (stage) {
            case 1 -> withPicture ? 1 : 2;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            case 5 -> 5;
            default -> g6 ? 6 : 2 + random.nextInt(4);
        };
        return switch (kind) {
            case 1 -> shaded(id);
            case 2 -> biggest(id, grade);
            case 3 -> equivalent(id);
            case 4 -> sameDenominator(id, grade);
            case 5 -> partOf(id, grade);
            default -> differentDenominators(id);
        };
    }

    /** "איזה חלק מהעיגול צבוע?" - picture: a pie or bar with n of d parts coloured. */
    private QuestionResponse shaded(int id) {
        int d = rand(2, 8);
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
        int k = rand(2, 4);
        String correct = frac(b[0] * k, b[1] * k);
        List<String> wrong = List.of(frac(b[0] * k, b[1] * k + 1), frac(b[0] + k, b[1] + k), frac(b[0] * k + 1, b[1] * k));
        return text(id, Topic.FRACTIONS, "איזה שבר שווה ל-" + frac(b[0], b[1]) + "?", correct, wrong,
                "שבר לא משתנה כשכופלים את המספר למעלה ואת המספר למטה באותו מספר.",
                frac(b[0], b[1]) + " = " + correct + " (כפול " + k + " למעלה ולמטה).", null);
    }

    /** "2/7 + 3/7 = ?" - only the numerators change. The classic mistake (adding the bottoms) is an option. */
    private QuestionResponse sameDenominator(int id, Grade grade) {
        int d = rand(5, grade == Grade.G6 ? 12 : 10);
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
        int whole = d * rand(2, grade == Grade.G6 ? 12 : 8);
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

    QuestionResponse geometry(int id, int stage, boolean withPicture) {
        int kind = stage >= 6 ? (withPicture ? 6 : 1 + random.nextInt(5)) : stage;
        return switch (kind) {
            case 1 -> perimeter(id);
            case 2 -> area(id);
            case 3 -> square(id);
            case 4 -> angles(id);
            case 5 -> triangleArea(id);
            default -> random.nextBoolean() ? sides(id) : (random.nextBoolean() ? angles(id) : triangleArea(id));
        };
    }

    private QuestionResponse perimeter(int id) {
        int w = rand(3, 15), h = rand(2, 12);
        if (w == h) w++;
        int r = 2 * (w + h);
        return number(id, "מה ההיקף של מלבן שאורכו " + w + " ס\"מ ורוחבו " + h + " ס\"מ?", r,
                List.of(w + h, w * h, 2 * w + h),
                "היקף = כל הצלעות מסביב. במלבן יש 2 אורכים ו-2 רוחבים.",
                w + " + " + h + " + " + w + " + " + h + " = " + r + " ס\"מ.", new VisualModel("RECT", null, w, h, 0, null));
    }

    private QuestionResponse area(int id) {
        int w = rand(3, 12), h = rand(2, 9);
        if (w == h) w++;
        int r = w * h;
        return number(id, "מה השטח של מלבן שאורכו " + w + " ס\"מ ורוחבו " + h + " ס\"מ? (בסמ\"ר)", r,
                List.of(2 * (w + h), w + h, r + w),
                "שטח מלבן = אורך × רוחב (כמה ריבועים של 1 ס\"מ נכנסים בתוכו).",
                w + " × " + h + " = " + r + " סמ\"ר.", new VisualModel("RECT", null, w, h, 0, null));
    }

    private QuestionResponse square(int id) {
        int s = rand(2, 12);
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
        int base = 2 * rand(2, 8), height = rand(2, 10);
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
        return number(id, "כמה צלעות יש לצורה?", n, List.of(n - 1, n + 1, n == 8 ? 6 : n + 2),
                "צלע היא קו ישר אחד בשפת הצורה. סופרים אותן מסביב.",
                "יש " + n + " צלעות - זה " + names[n] + ".", new VisualModel("POLYGON", null, n, 0, 0, null));
    }

    // ---- helpers --------------------------------------------------------------------------------

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
        return text(id, Topic.GEOMETRY, text, String.valueOf(correct), w, hint, explanation, visual);
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
                    : String.valueOf(Integer.parseInt(correct) + bump));
            bump++;
        }
        List<String> list = new ArrayList<>(options);
        Collections.shuffle(list, random);
        return new QuestionResponse(id, topic, text, list, list.indexOf(correct), hint, explanation, visual);
    }
}
