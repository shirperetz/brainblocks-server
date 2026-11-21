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
 * 2026-11-21 (Shir, overnight: "all the material for grades 1-6, by the Ministry's curriculum and the
 * מיצ"ב"). The topics that were missing: זוגי/אי-זוגי, השלמה לעשר, סדר פעולות, עיגול ואומדן,
 * התחלקות וראשוניים, מדידות, עשרוניים, אחוזים, יחס, נתונים וסיכוי, עיגול, נפח, משוואות.
 * <p>
 * Same rules as the other generators: every answer is computed here, the three wrong answers are
 * the mistakes children really make (left-to-right instead of × first, rounding down, 0.45 > 0.5
 * "because 45 > 5"...), and the right answer's place among them is random. Each topic has 6 levels;
 * a grade gets only the levels of its grade ({@link #levelCap}) - a higher grade's level only when a
 * teacher approved it (the caller then passes that grade). A stage beyond the cap repeats the cap,
 * so a very good child gets the hardest of HER grade, never the next grade's material.
 * About 60% of a stage is its own level, 40% earlier levels (like the fractions).
 */
public final class ExtraTopicsQuestions {

    private final Random random;

    ExtraTopicsQuestions(Random random) {
        this.random = random;
    }

    public static boolean handles(Topic t) {
        return switch (t) {
            case EVEN_ODD, MAKE_TEN, ORDER_OPS, ROUNDING, DIVISIBILITY, MEASURE, DECIMALS, PERCENT, RATIO,
                 DATA, CIRCLE, VOLUME, EQUATIONS -> true;
            default -> false;
        };
    }

    /** The highest level (1-6) of a topic a grade gets; 0 = not this grade's material. */
    public static int levelCap(Topic t, Grade g) {
        int k = g == null ? 3 : g.ordinal() + 1; // 1..6
        int[] byGrade = switch (t) {
            //                 א  ב  ג  ד  ה  ו
            case EVEN_ODD ->     new int[] {3, 5, 6, 6, 6, 6};
            case MAKE_TEN ->     new int[] {3, 6, 6, 6, 6, 6};
            case ORDER_OPS ->    new int[] {0, 0, 3, 5, 6, 6};
            case ROUNDING ->     new int[] {0, 0, 3, 6, 6, 6};
            case DIVISIBILITY -> new int[] {0, 0, 1, 4, 6, 6};
            case MEASURE ->      new int[] {0, 2, 3, 5, 6, 6};
            case DECIMALS ->     new int[] {0, 0, 0, 1, 5, 6};
            case PERCENT ->      new int[] {0, 0, 0, 0, 0, 6};
            case RATIO ->        new int[] {0, 0, 0, 0, 0, 6};
            case DATA ->         new int[] {0, 2, 3, 5, 6, 6};
            case CIRCLE ->       new int[] {0, 0, 0, 0, 0, 6};
            case VOLUME ->       new int[] {0, 0, 0, 0, 3, 6};
            case EQUATIONS ->    new int[] {0, 0, 2, 3, 4, 6};
            default -> new int[] {0, 0, 0, 0, 0, 0};
        };
        return byGrade[k - 1];
    }

    QuestionResponse question(int id, Topic t, Grade g, int stage) {
        return question(id, t, g, stage, false);
    }

    /**
     * [hard] (the child got >= 90% lately - AdaptiveDifficulty): no "small numbers first" and no
     * going back to easier levels - the hardest of the stage, still inside the grade's levels.
     */
    QuestionResponse question(int id, Topic t, Grade g, int stage, boolean hard) {
        int cap = Math.max(1, levelCap(t, g));
        int top = Math.min(Math.max(stage, 1), cap);
        int level = hard || top == 1 || random.nextInt(10) < 6 ? top : 1 + random.nextInt(top - 1);
        small = !hard && id < 5; // the first questions of a round: small numbers
        this.grade = g == null ? Grade.G3 : g;
        return switch (t) {
            case EVEN_ODD -> evenOdd(id, level);
            case MAKE_TEN -> makeTen(id, level);
            case ORDER_OPS -> orderOps(id, level);
            case ROUNDING -> rounding(id, level);
            case DIVISIBILITY -> divisibility(id, level);
            case MEASURE -> measure(id, level);
            case DECIMALS -> decimals(id, level);
            case PERCENT -> percent(id, level);
            case RATIO -> ratio(id, level);
            case DATA -> data(id, level);
            case CIRCLE -> circle(id, level);
            case VOLUME -> volume(id, level);
            default -> equations(id, level);
        };
    }

    private boolean small = true;
    private Grade grade = Grade.G3;

    // ---- 1. זוגי ואי-זוגי (א׳-ג׳) ------------------------------------------------------------

    private QuestionResponse evenOdd(int id, int level) {
        int max = switch (level) { case 1 -> 10; case 2 -> 20; case 3 -> 50; case 4 -> 100; default -> 1000; };
        if (small) max = Math.min(max, 20);
        switch (level) {
            case 1, 2, 5 -> {
                boolean even = random.nextBoolean();
                // one number of the asked kind among three of the other kind
                int right = pickParity(max, even);
                List<String> wrong = new ArrayList<>();
                while (wrong.size() < 3) {
                    String w = String.valueOf(pickParity(max, !even));
                    if (!wrong.contains(w)) wrong.add(w);
                }
                return text(id, Topic.EVEN_ODD, "איזה מספר " + (even ? "זוגי" : "אי-זוגי") + "?", String.valueOf(right), wrong,
                        "מספר זוגי נגמר ב-0, 2, 4, 6 או 8 - אפשר לחלק אותו לשני חלקים שווים. אי-זוגי נגמר ב-1, 3, 5, 7 או 9.",
                        right + " נגמר ב-" + (right % 10) + ", ולכן הוא " + (even ? "זוגי" : "אי-זוגי") + ".", null)
                        .withSkill("EVEN_ODD_PICK");
            }
            case 3 -> {
                int n = rand(3, max - 3);
                boolean even = random.nextBoolean();
                int next = n + 1;
                while ((next % 2 == 0) != even) next++;
                return num(id, Topic.EVEN_ODD, "מה המספר ה" + (even ? "זוגי" : "אי-זוגי") + " הבא אחרי " + n + "?", next,
                        List.of(next + 1, next + 2, n, next - 2 > n ? next - 2 : next + 3),
                        "סופרים קדימה מ-" + n + " ובודקים כל מספר: נגמר ב-0,2,4,6,8 - זוגי.",
                        "אחרי " + n + " בא " + (n + 1) + (next == n + 1 ? "" : ", ואחריו " + next) + " - והוא " + (even ? "זוגי" : "אי-זוגי") + ".", null)
                        .withSkill("EVEN_ODD_NEXT");
            }
            case 4 -> {
                int a = rand(1, max - 12);
                int b = a + rand(5, 10);
                int count = 0;
                for (int x = a; x <= b; x++) if (x % 2 == 0) count++;
                return num(id, Topic.EVEN_ODD, "כמה מספרים זוגיים יש מ-" + a + " עד " + b + " (כולל שניהם)?", count,
                        List.of(count + 1, count - 1, b - a + 1, count + 2),
                        "כותבים את המספרים מ-" + a + " עד " + b + " ומסמנים כל שני.",
                        "הזוגיים הם: " + evens(a, b) + " - " + count + " מספרים.", null)
                        .withSkill("EVEN_ODD_COUNT");
            }
            default -> {
                // 6: odd + odd / even + odd ... -> which result
                int a = rand(11, 99), b = rand(11, 99);
                int sum = a + b;
                boolean even = sum % 2 == 0;
                String[] opts = {"זוגי", "אי-זוגי"};
                List<String> o = new ArrayList<>(List.of(opts));
                return new QuestionResponse(id, Topic.EVEN_ODD, "בלי לחשב: " + a + " + " + b + " - התוצאה זוגית או אי-זוגית?",
                        o, even ? 0 : 1,
                        "מספיק להסתכל על ספרת האחדות: זוגי+זוגי=זוגי, אי-זוגי+אי-זוגי=זוגי, זוגי+אי-זוגי=אי-זוגי.",
                        a + " הוא " + par(a) + ", " + b + " הוא " + par(b) + ", ולכן התוצאה " + (even ? "זוגית" : "אי-זוגית") + " (" + sum + ").", null)
                        .withSkill("EVEN_ODD_RULE");
            }
        }
    }

    static String hours(int h) {
        return h == 1 ? "שעה אחת" : h == 2 ? "שעתיים" : h + " שעות";
    }

    private static String par(int n) { return n % 2 == 0 ? "זוגי" : "אי-זוגי"; }

    private String evens(int a, int b) {
        List<String> s = new ArrayList<>();
        for (int x = a; x <= b; x++) if (x % 2 == 0) s.add(String.valueOf(x));
        return String.join(", ", s);
    }

    private int pickParity(int max, boolean even) {
        int x = rand(1, max);
        if ((x % 2 == 0) != even) x = x + 1 <= max ? x + 1 : x - 1;
        return x;
    }

    // ---- 2. השלמה לעשר / לעשרת / ל-100 (א׳-ב׳) ----------------------------------------------

    private QuestionResponse makeTen(int id, int level) {
        int target, n;
        switch (level) {
            case 1 -> { target = 10; n = rand(1, 9); }
            case 2 -> { target = 20; n = rand(11, 19); }
            case 3 -> { target = 20; n = rand(2, 18); }
            case 4 -> { target = 100; n = 10 * rand(1, 9); }
            case 5 -> { n = rand(11, 89); if (n % 10 == 0) n++; target = (n / 10 + 1) * 10; }
            default -> { target = 100; n = rand(11, 89); if (n % 10 == 0) n += 3; }
        }
        int ans = target - n;
        boolean first = random.nextBoolean();
        String q = first ? n + " + ? = " + target : "? + " + n + " = " + target;
        return num(id, Topic.MAKE_TEN, "איזה מספר חסר? " + q, ans,
                List.of(ans + 1, ans - 1, target == 100 && ans % 10 != 0 ? ans + 10 : ans + 2, n),
                target == 10 ? "חברים של 10: 1+9, 2+8, 3+7, 4+6, 5+5." :
                        target == 100 && level == 6 ? "קודם משלימים לעשרת הבאה, ואחר כך סופרים עשרות עד 100." :
                        "סופרים מ-" + n + " עד " + target + ".",
                n + " + " + ans + " = " + target + ".", null)
                .withSkill(target == 10 ? "MAKE_10" : target == 20 ? "MAKE_20" : level == 5 ? "MAKE_NEXT_TEN" : "MAKE_100");
    }

    // ---- 3. סדר פעולות וסוגריים (ג׳-ו׳) ------------------------------------------------------

    private QuestionResponse orderOps(int id, int level) {
        int m = small ? 5 : grade.ordinal() >= Grade.G5.ordinal() ? 12 : 9;
        String expr;
        int right, leftToRight;
        String why;
        switch (level) {
            case 1 -> { // a + b × c
                int a = rand(2, 20), b = rand(2, m), c = rand(2, m);
                expr = a + " + " + b + " × " + c; right = a + b * c; leftToRight = (a + b) * c;
                why = "קודם כפל: " + b + " × " + c + " = " + b * c + ", ואז " + a + " + " + b * c + " = " + right + ".";
            }
            case 2 -> { // a × b - c
                int a = rand(2, m), b = rand(2, m), c = rand(1, Math.min(20, a * b - 1));
                boolean front = random.nextBoolean();
                if (front) {
                    int big = a * b + rand(5, 30);
                    expr = big + " - " + a + " × " + b; right = big - a * b; leftToRight = (big - a) * b;
                    why = "קודם כפל: " + a + " × " + b + " = " + a * b + ", ואז " + big + " - " + a * b + " = " + right + ".";
                } else {
                    expr = a + " × " + b + " - " + c; right = a * b - c; leftToRight = a * (b - c);
                    why = "קודם כפל: " + a + " × " + b + " = " + a * b + ", ואז פחות " + c + ": " + right + ".";
                }
            }
            case 3 -> { // (a + b) × c
                int a = rand(2, m), b = rand(2, m), c = rand(2, m);
                expr = "(" + a + " + " + b + ") × " + c; right = (a + b) * c; leftToRight = a + b * c;
                why = "קודם מה שבסוגריים: " + a + " + " + b + " = " + (a + b) + ", ואז × " + c + " = " + right + ".";
            }
            case 4 -> { // a + b ÷ c
                int c = rand(2, m), q = rand(2, m), a = rand(2, 30);
                int b = c * q;
                expr = a + " + " + b + " ÷ " + c; right = a + q;
                leftToRight = (a + b) % c == 0 ? (a + b) / c : right + c;
                why = "קודם חילוק: " + b + " ÷ " + c + " = " + q + ", ואז " + a + " + " + q + " = " + right + ".";
            }
            case 5 -> { // a × b + c × d
                int a = rand(2, m), b = rand(2, m), c = rand(2, m), d = rand(2, m);
                expr = a + " × " + b + " + " + c + " × " + d; right = a * b + c * d; leftToRight = (a * b + c) * d;
                why = a + " × " + b + " = " + a * b + ", " + c + " × " + d + " = " + c * d + ", ביחד " + right + ".";
            }
            default -> { // a × (b - c) + d
                int a = rand(2, m), c = rand(1, 8), b = c + rand(1, m), d = rand(1, 20);
                expr = a + " × (" + b + " - " + c + ") + " + d; right = a * (b - c) + d; leftToRight = a * b - c + d;
                why = "סוגריים: " + b + " - " + c + " = " + (b - c) + ", כפל: " + a + " × " + (b - c) + " = " + a * (b - c) + ", ועוד " + d + " = " + right + ".";
            }
        }
        return num(id, Topic.ORDER_OPS, "כמה זה " + expr + "?", right,
                List.of(leftToRight, right + 10, right - 1, right + 2),
                "קודם מה שבתוך סוגריים, אחר כך כפל וחילוק, ורק בסוף חיבור וחיסור.",
                why, null).withSkill(level <= 2 ? "OPS_MUL_FIRST" : level == 3 || level == 6 ? "OPS_BRACKETS" : "OPS_MIXED");
    }

    // ---- 4. עיגול ואומדן (ג׳-ה׳) -------------------------------------------------------------

    private QuestionResponse rounding(int id, int level) {
        if (level <= 4) {
            int to = level <= 2 ? 10 : level == 3 ? 100 : 1000;
            int n = switch (level) {
                case 1 -> rand(11, 99);
                case 2 -> rand(101, 999);
                case 3 -> rand(101, small ? 999 : 9999);
                default -> rand(1001, 99999);
            };
            if (n % to == 0) n += to / 3;
            int down = n / to * to, up = down + to;
            boolean half = n - down >= to / 2;
            int right = half ? up : down;
            String place = to == 10 ? "לעשרות" : to == 100 ? "למאות" : "לאלפים";
            return num(id, Topic.ROUNDING, "עגלו את " + n + " " + place + " הקרובות", right,
                    List.of(half ? down : up, roundTo(n, to * 10), right + to, right - to),
                    "מסתכלים על הספרה שאחרי המקום שמעגלים אליו: 0-4 - למטה, 5-9 - למעלה.",
                    n + " בין " + down + " ל-" + up + ", והוא קרוב יותר ל-" + right + (n - down == to / 2 ? " (באמצע - מעגלים למעלה)" : "") + ".", null)
                    .withSkill("ROUND_" + to);
        }
        if (level == 5) { // estimate a sum by rounding to hundreds
            int a = rand(2, 9) * 100 + randNear(), b = rand(1, 8) * 100 + randNear();
            int ra = roundTo(a, 100), rb = roundTo(b, 100);
            int est = ra + rb;
            return num(id, Topic.ROUNDING, "בערך כמה זה " + a + " + " + b + "? (עגלו כל מספר למאות)", est,
                    List.of(est + 100, est - 100, est + 1000, a + b == est ? est + 200 : a + b),
                    "מעגלים כל מספר למאה הקרובה, ומחברים את המספרים העגולים - זה אומדן.",
                    a + " ≈ " + ra + ", " + b + " ≈ " + rb + ", ולכן בערך " + est + ".", null).withSkill("ESTIMATE_SUM");
        }
        int a = rand(2, 9) * 10 + (random.nextBoolean() ? 1 : -1), b = rand(2, 9) * 10 + (random.nextBoolean() ? 1 : -1);
        int est = roundTo(a, 10) * roundTo(b, 10);
        return num(id, Topic.ROUNDING, "בערך כמה זה " + a + " × " + b + "? (עגלו לעשרות)", est,
                List.of(est * 10, est / 10, est + roundTo(a, 10), est - roundTo(b, 10)),
                "מעגלים כל מספר לעשרת הקרובה, כופלים את הספרות ומוסיפים את האפסים.",
                a + " ≈ " + roundTo(a, 10) + ", " + b + " ≈ " + roundTo(b, 10) + ", ולכן בערך " + est + ".", null).withSkill("ESTIMATE_PRODUCT");
    }

    private int randNear() {
        int r = rand(1, 45);
        return random.nextBoolean() ? r : -r;
    }

    private static int roundTo(int n, int to) {
        return (int) (Math.floor((n + to / 2.0) / to) * to);
    }

    // ---- 5. התחלקות, מחלקים, כפולות וראשוניים (ג׳-ו׳) ------------------------------------------

    private static final int[] PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};

    private QuestionResponse divisibility(int id, int level) {
        switch (level) {
            case 1, 2, 3 -> {
                int d = level == 1 ? new int[] {2, 5, 10}[random.nextInt(3)] : level == 2 ? 3 : (random.nextBoolean() ? 9 : 3);
                int max = small ? 200 : 2000;
                int right = d * rand(3, max / d);
                List<String> wrong = new ArrayList<>();
                int guard = 0;
                int below = rand(0, 3); // how many wrong ones are smaller - "pick the middle" can't know
                while (wrong.size() < 3 && guard++ < 200) {
                    int w = wrong.size() < below ? right - rand(1, 40) : right + rand(1, 40);
                    if (w > 0 && w % d != 0 && !wrong.contains(String.valueOf(w))
                            ) wrong.add(String.valueOf(w));
                }
                String hint = switch (d) {
                    case 2 -> "מתחלק ב-2 - כשספרת האחדות זוגית.";
                    case 5 -> "מתחלק ב-5 - כשהמספר נגמר ב-0 או ב-5.";
                    case 10 -> "מתחלק ב-10 - כשהמספר נגמר ב-0.";
                    case 3 -> "מתחלק ב-3 - כשסכום הספרות מתחלק ב-3.";
                    default -> "מתחלק ב-9 - כשסכום הספרות מתחלק ב-9.";
                };
                return text(id, Topic.DIVISIBILITY, "איזה מספר מתחלק ב-" + d + " בלי שארית?", String.valueOf(right), wrong, hint,
                        right + " : " + d + " = " + right / d + (d == 3 || d == 9 ? " (סכום הספרות: " + digitSum(right) + ")" : "") + ".", null)
                        .withSkill("DIV_RULE_" + d);
            }
            case 4 -> {
                int n = new int[] {12, 18, 20, 24, 30, 36, 40, 42, 48, 60}[random.nextInt(10)];
                List<Integer> divs = new ArrayList<>();
                for (int x = 2; x < n; x++) if (n % x == 0) divs.add(x);
                int right = divs.get(random.nextInt(divs.size()));
                List<String> wrong = new ArrayList<>();
                for (int x = 2; x < n && wrong.size() < 6; x++) if (n % x != 0 && Math.abs(x - right) <= 6) wrong.add(String.valueOf(x));
                Collections.shuffle(wrong, random);
                return text(id, Topic.DIVISIBILITY, "איזה מספר הוא מחלק של " + n + "?", String.valueOf(right), wrong,
                        "מחלק = מספר ש-" + n + " מתחלק בו בלי שארית. בודקים בלוח הכפל: מה כפול מה נותן " + n + "?",
                        right + " × " + n / right + " = " + n + ", ולכן " + right + " מחלק של " + n + ".", null).withSkill("DIVISORS");
            }
            case 5 -> {
                int a = new int[] {2, 3, 4, 5, 6}[random.nextInt(5)], b = new int[] {3, 4, 5, 6, 8, 9, 10}[random.nextInt(7)];
                if (a == b) b++;
                int lcm = a * b / FractionGeometryQuestions.gcd(a, b);
                return num(id, Topic.DIVISIBILITY, "מה הכפולה המשותפת הקטנה ביותר של " + a + " ושל " + b + "?", lcm,
                        List.of(a * b == lcm ? lcm * 2 : a * b, lcm + a, Math.max(a, b), lcm + b),
                        "כותבים את הכפולות של " + Math.max(a, b) + " (" + Math.max(a, b) + ", " + 2 * Math.max(a, b) + ", ...) ובודקים מתי גם " + Math.min(a, b) + " נכנס.",
                        lcm + " מתחלק גם ב-" + a + " וגם ב-" + b + ", ואין מספר קטן יותר כזה.", null).withSkill("LCM");
            }
            default -> {
                boolean askPrime = random.nextInt(3) > 0;
                if (askPrime) {
                    int p = PRIMES[rand(small ? 1 : 4, small ? 9 : PRIMES.length - 1)];
                    List<String> wrong = new ArrayList<>();
                    int guard = 0;
                    while (wrong.size() < 3 && guard++ < 300) {
                        int w = p + rand(-10, 10);
                        if (w > 3 && !isPrime(w) && w % 2 == 1 && !wrong.contains(String.valueOf(w))) wrong.add(String.valueOf(w));
                    }
                    while (wrong.size() < 3) wrong.add(String.valueOf(p + 1 + wrong.size() * 2 + (p % 2 == 1 ? 0 : 1)));
                    return text(id, Topic.DIVISIBILITY, "איזה מספר הוא ראשוני?", String.valueOf(p), wrong,
                            "מספר ראשוני מתחלק רק ב-1 ובעצמו. בודקים אם הוא מתחלק ב-3, ב-5, ב-7...",
                            p + " לא מתחלק באף מספר חוץ מ-1 ומ-" + p + ", ולכן הוא ראשוני. (שאר המספרים מתחלקים במשהו.)", null).withSkill("PRIMES");
                }
                int a = new int[] {12, 18, 24, 30, 36, 40}[random.nextInt(6)], b = new int[] {16, 20, 27, 42, 45, 48}[random.nextInt(6)];
                int g = FractionGeometryQuestions.gcd(a, b);
                if (g == 1) { a = 12; b = 18; g = 6; }
                return num(id, Topic.DIVISIBILITY, "מה המחלק המשותף הגדול ביותר של " + a + " ושל " + b + "?", g,
                        List.of(g == 2 ? 4 : g / 2, g + 1, Math.min(a, b) - g > 0 && Math.min(a, b) != g ? Math.min(a, b) : g * 2, g * 2),
                        "מחפשים את המספר הגדול ביותר שגם " + a + " וגם " + b + " מתחלקים בו.",
                        a + " : " + g + " = " + a / g + ", " + b + " : " + g + " = " + b / g + ", ואין מספר גדול יותר שמחלק את שניהם.", null).withSkill("GCD");
            }
        }
    }

    private static boolean isPrime(int n) {
        if (n < 2) return false;
        for (int d = 2; d * d <= n; d++) if (n % d == 0) return false;
        return true;
    }

    private static int digitSum(int n) {
        int s = 0;
        for (char c : String.valueOf(n).toCharArray()) s += c - '0';
        return s;
    }

    // ---- 6. מדידות והמרות (ב׳-ו׳) ------------------------------------------------------------

    private QuestionResponse measure(int id, int level) {
        switch (level) {
            case 1 -> { // מטר <-> ס"מ
                int m = rand(1, small ? 3 : 9);
                return num(id, Topic.MEASURE, m + " מטר = כמה סנטימטרים?", m * 100,
                        List.of(m * 10, m * 1000, m + 100, (m + 1) * 100),
                        "במטר אחד יש 100 סנטימטרים.", m + " × 100 = " + m * 100 + " ס״מ.", null).withSkill("MEAS_CM_M");
            }
            case 2 -> { // שעות -> דקות / חצי שעה
                if (random.nextBoolean()) {
                    int h = rand(1, small ? 3 : 5);
                    return num(id, Topic.MEASURE, h + " שעות = כמה דקות?", h * 60,
                            List.of(h * 100, h * 30, h * 60 + 60, h * 10),
                            "בשעה אחת יש 60 דקות.", h + " × 60 = " + h * 60 + " דקות.", null).withSkill("MEAS_TIME");
                }
                int h = rand(1, 3);
                return num(id, Topic.MEASURE, h + " שעות וחצי = כמה דקות?", h * 60 + 30,
                        List.of(h * 60 + 50, h * 100 + 30, h * 60 + 60, h * 60),
                        "בשעה 60 דקות, וחצי שעה זה 30 דקות.", h + " × 60 + 30 = " + (h * 60 + 30) + " דקות.", null).withSkill("MEAS_TIME");
            }
            case 3 -> { // ק"ג <-> גרם, ליטר <-> מ"ל
                boolean kg = random.nextBoolean();
                int x = rand(1, small ? 4 : 9);
                String big = kg ? "קילוגרם" : "ליטר", tiny = kg ? "גרם" : "מיליליטר";
                return num(id, Topic.MEASURE, x + " " + big + " = כמה " + tiny + "?", x * 1000,
                        List.of(x * 100, x * 10, x * 10000, (x + 1) * 1000),
                        "ב" + big + " אחד יש 1,000 " + tiny + ".", x + " × 1,000 = " + x * 1000 + " " + tiny + ".", null)
                        .withSkill(kg ? "MEAS_KG_G" : "MEAS_L_ML");
            }
            case 4 -> { // מורכב: 2 מטר ו-35 ס"מ = ? ס"מ ; 3 ק"מ = ? מטר
                if (random.nextBoolean()) {
                    int m = rand(1, 9), cm = rand(5, 95);
                    return num(id, Topic.MEASURE, m + " מטר ו-" + cm + " סנטימטרים = כמה סנטימטרים?", m * 100 + cm,
                            List.of(m * 10 + cm, m * 1000 + cm, m + cm, m * 100 + cm + 100),
                            "קודם הופכים את המטרים לסנטימטרים (×100), ואז מוסיפים את הסנטימטרים.",
                            m + " מטר = " + m * 100 + " ס״מ, ועוד " + cm + " = " + (m * 100 + cm) + " ס״מ.", null).withSkill("MEAS_MIXED");
                }
                int km = rand(2, 9);
                return num(id, Topic.MEASURE, km + " קילומטר = כמה מטרים?", km * 1000,
                        List.of(km * 100, km * 10000, km * 10, (km + 1) * 1000),
                        "בקילומטר אחד יש 1,000 מטרים.", km + " × 1,000 = " + km * 1000 + " מטר.", null).withSkill("MEAS_KM_M");
            }
            case 5 -> { // ההפך: 3,500 גרם = ? ק"ג ו-? גרם ; 150 דקות = ? שעות ו-? דקות
                if (random.nextBoolean()) {
                    int kg = rand(1, 8), g = rand(1, 9) * 100;
                    String right = kg + " ק״ג ו-" + g + " גרם";
                    return text(id, Topic.MEASURE, (kg * 1000 + g) + " גרם = ?", right,
                            List.of((kg * 10) + " ק״ג ו-" + g + " גרם", kg + " ק״ג ו-" + g / 100 + " גרם", (kg + 1) + " ק״ג ו-" + g + " גרם", kg + g / 100 + " ק״ג"),
                            "כל 1,000 גרם הם קילוגרם אחד. בודקים כמה אלפים יש, ומה שנשאר - גרמים.",
                            (kg * 1000 + g) + " גרם = " + kg + " אלפים ועוד " + g + " = " + right + ".", null).withSkill("MEAS_BACK");
                }
                int h = rand(1, 4), mins = rand(1, 5) * 10;
                String right = hours(h) + " ו-" + mins + " דקות";
                return text(id, Topic.MEASURE, (h * 60 + mins) + " דקות = ?", right,
                        List.of(hours((h * 60 + mins) / 100) + " ו-" + (h * 60 + mins) % 100 + " דקות", hours(h + 1) + " ו-" + mins + " דקות",
                                hours(h) + " ו-" + (mins + 30) + " דקות", hours(h - 1 > 0 ? h - 1 : h + 2) + " ו-" + mins + " דקות"),
                        "כל 60 דקות הן שעה. בודקים כמה פעמים 60 נכנס, ומה שנשאר - דקות.",
                        h + " × 60 = " + h * 60 + ", ועוד " + mins + " דקות.", null).withSkill("MEAS_BACK");
            }
            default -> { // עשרוני: 1.5 ק"מ = ? מטר ; 2.5 ק"ג = ? גרם
                int whole = rand(1, 6), tenth = rand(1, 9);
                boolean km = random.nextBoolean();
                int right = whole * 1000 + tenth * 100;
                return num(id, Topic.MEASURE, whole + "." + tenth + " " + (km ? "קילומטר = כמה מטרים" : "קילוגרם = כמה גרמים") + "?", right,
                        List.of(whole * 1000 + tenth, whole * 1000 + tenth * 10, whole * 100 + tenth * 10, right + 1000),
                        "כופלים ב-1,000: הנקודה זזה 3 מקומות שמאלה (" + whole + "." + tenth + " = " + whole + "." + tenth + "00).",
                        whole + "." + tenth + " × 1,000 = " + right + ".", null).withSkill("MEAS_DECIMAL");
            }
        }
    }

    // ---- 7. מספרים עשרוניים (ד׳-ו׳) -----------------------------------------------------------

    private QuestionResponse decimals(int id, int level) {
        switch (level) {
            case 1 -> {
                int w = rand(0, small ? 3 : 20), t = rand(1, 9);
                if (w == 0) w = 1 + rand(0, small ? 3 : 20);
                String right = w + "." + t;
                String wholes = w == 1 ? "שלם אחד" : w + " שלמים", tenths = t == 1 ? "עשירית אחת" : t + " עשיריות";
                String words = random.nextBoolean() ? wholes + " ו-" + tenths : tenths + " ו-" + wholes;
                return text(id, Topic.DECIMALS, "איך כותבים " + words + "?", right,
                        List.of(w + ".0" + t, t + "." + w, (w * 10 + t) + "", w + "." + (t + 1 <= 9 ? t + 1 : t - 1)),
                        "הספרה הראשונה אחרי הנקודה היא העשיריות.", words + " = " + right + ".", null).withSkill("DEC_WRITE");
            }
            case 2 -> { // which is bigger - the 0.5 vs 0.45 trap
                int a = rand(2, 8);                  // 0.a
                int b = (a - 1) * 10 + rand(1, 9);   // 0.bb < 0.a although "bb > a" (0.45 vs 0.5)
                List<String> o = new ArrayList<>(List.of("0." + a, "0." + b));
                boolean askBig = random.nextBoolean();
                double va = a / 10.0, vb = b / 100.0;
                String right = (askBig == (va > vb)) ? "0." + a : "0." + b;
                o.add("הם שווים");
                Collections.shuffle(o, random);
                return new QuestionResponse(id, Topic.DECIMALS, "איזה מספר " + (askBig ? "גדול" : "קטן") + " יותר?", o, o.indexOf(right),
                        "משווים קודם את העשיריות (הספרה הראשונה אחרי הנקודה). אפשר להוסיף 0: 0." + a + " = 0." + a + "0.",
                        "0." + a + " = 0." + a + "0, ו-" + a + "0 מאיות " + (va > vb ? "יותר" : "פחות") + " מ-" + b + " מאיות.", null).withSkill("DEC_COMPARE");
            }
            case 3 -> { // + / - tenths with wholes
                int a = rand(1, 50), b = rand(1, 30);
                boolean plus = random.nextBoolean() || a <= b;
                int r = plus ? a + b : a - b;
                return decNum(id, Topic.DECIMALS, dec(a, 10) + (plus ? " + " : " - ") + dec(b, 10) + " = ?", r * 10,
                        List.of((r + 10) * 10, (r - 1) * 10, r, (r + 1) * 10, (r - 10) * 10, (r + 2) * 10), 10,
                        "כותבים את המספרים אחד מעל השני כך שהנקודות בדיוק זו מעל זו, ומחשבים כרגיל.",
                        dec(a, 10) + (plus ? " + " : " - ") + dec(b, 10) + " = " + dec(r, 10) + ".", null).withSkill("DEC_ADD");
            }
            case 4 -> { // × / ÷ 10, 100
                int x = rand(11, 999);
                if (x % 10 == 0) x++;
                boolean times = random.nextBoolean();
                int k = random.nextBoolean() ? 10 : 100;
                String q = dec(x, 100) + (times ? " × " : " ÷ ") + k;
                String right = times ? dec(x * k, 100) : dec(x, 100 * k);
                return text(id, Topic.DECIMALS, q + " = ?", right,
                        List.of(times ? dec(x, 100 * k) : dec(x * k, 100),            // moved the wrong way
                                times ? dec(x * k * 10, 100) : dec(x, 1000 * k),      // one place too far
                                dec(x, 100),                                           // didn't move
                                times ? dec(x * k, 1000) : dec(x * 10, 100 * k)),     // one place too few
                        "כפל ב-10 מזיז את הנקודה מקום אחד ימינה (המספר גדל), חילוק ב-10 - מקום אחד שמאלה. ב-100 - שני מקומות.",
                        q + " = " + right + ".", null).withSkill("DEC_POWERS_10");
            }
            case 5 -> { // hundredths + / fraction -> decimal
                if (random.nextBoolean()) {
                    int[][] p = {{1, 2, 50}, {1, 4, 25}, {3, 4, 75}, {1, 5, 20}, {2, 5, 40}, {3, 5, 60}, {1, 10, 10}, {7, 10, 70}, {1, 20, 5}, {3, 20, 15}};
                    int[] f = p[random.nextInt(p.length)];
                    String right = dec(f[2], 100);
                    return text(id, Topic.DECIMALS, "איך כותבים את " + f[0] + "/" + f[1] + " כמספר עשרוני?", right,
                            List.of(dec(f[0], 100), dec(f[0], 10), dec(f[2], 1000), dec(f[2] + 5, 100)),
                            "מחפשים שבר שווה עם 100 למטה: כמה מאיות זה?",
                            f[0] + "/" + f[1] + " = " + f[2] + "/100 = " + right + ".", null).withSkill("DEC_FROM_FRACTION");
                }
                int a = rand(105, 999), b = rand(15, 450);
                return text(id, Topic.DECIMALS, dec(a, 100) + " + " + dec(b, 100) + " = ?", dec(a + b, 100),
                        List.of(dec(a + b + 100, 100), dec(a + b - 10, 100), dec(a + b, 10), dec(a / 10 + b, 100)),
                        "הנקודה מתחת לנקודה, מאיות מתחת למאיות. חסר ספרה? מוסיפים 0.",
                        dec(a, 100) + " + " + dec(b, 100) + " = " + dec(a + b, 100) + ".", null).withSkill("DEC_ADD_HUNDREDTHS");
            }
            default -> { // ו׳: עשרוני × שלם
                int a = rand(2, small ? 9 : 45), k = rand(2, 9);
                int r = a * k;
                return decNum(id, Topic.DECIMALS, dec(a, 10) + " × " + k + " = ?", r * 10,
                        List.of(r, r * 100, (a + k) * 10, (r + k) * 10, (r - k) * 10), 10,
                        "מחשבים בלי הנקודה (" + a + " × " + k + "), ואז מחזירים את הנקודה: ספרה אחת אחריה.",
                        a + " × " + k + " = " + r + ", ולכן " + dec(a, 10) + " × " + k + " = " + dec(r, 10) + ".", null).withSkill("DEC_TIMES_WHOLE");
            }
        }
    }

    /** n / scale written as a decimal, without trailing zeros ("12.5", "0.05", "3"). */
    static String dec(int n, int scale) {
        java.math.BigDecimal v = java.math.BigDecimal.valueOf(n).divide(java.math.BigDecimal.valueOf(scale));
        v = v.stripTrailingZeros();
        String s = v.toPlainString();
        return s;
    }

    // ---- 8. אחוזים (ו׳) --------------------------------------------------------------------

    private QuestionResponse percent(int id, int level) {
        switch (level) {
            case 1 -> {
                int[][] p = {{50, 1, 2}, {25, 1, 4}, {75, 3, 4}, {10, 1, 10}, {20, 1, 5}, {40, 2, 5}};
                int[] f = p[random.nextInt(p.length)];
                String right = f[1] + "/" + f[2];
                return text(id, Topic.PERCENT, f[0] + "% - איזה שבר זה?", right,
                        List.of("1/" + f[0], f[0] + "/10", f[2] == 2 ? "1/4" : "1/2", f[2] + "/" + f[1]),
                        "אחוז = חלק מ-100. " + f[0] + "% = " + f[0] + "/100, ומצמצמים.",
                        f[0] + "% = " + f[0] + "/100 = " + right + ".", null).withSkill("PCT_FRACTION");
            }
            case 2 -> {
                int p = rand(1, 9) * 10;
                return num(id, Topic.PERCENT, "כמה זה " + p + "% מ-100?", p,
                        List.of(100 - p, p / 10, p * 10, p + 10),
                        "אחוז אחד מ-100 הוא 1.", p + "% מ-100 = " + p + ".", null).withSkill("PCT_OF_100");
            }
            case 3 -> {
                int n = rand(2, small ? 9 : 50) * 10;
                return num(id, Topic.PERCENT, "כמה זה 10% מ-" + n + "?", n / 10,
                        List.of(n - 10, n / 100 == 0 ? n / 10 + 10 : n / 100, 10, n / 10 + 1),
                        "10% זה עשירית: מחלקים ב-10.", n + " : 10 = " + n / 10 + ".", null).withSkill("PCT_10");
            }
            case 4 -> {
                int[] ps = {25, 50, 20, 75};
                int p = ps[random.nextInt(ps.length)];
                int base = p == 20 ? 5 : p == 50 ? 2 : 4;
                int n = base * rand(2, small ? 10 : 40);
                int r = n * p / 100;
                return num(id, Topic.PERCENT, "כמה זה " + p + "% מ-" + n + "?", r,
                        List.of(n - r == r ? r + base : n - r, p, n / 10, r + 1),
                        p + "% = " + (p == 25 ? "רבע" : p == 50 ? "חצי" : p == 20 ? "חמישית" : "שלושה רבעים") + ".",
                        p + "% מ-" + n + " = " + r + ".", null).withSkill("PCT_OF");
            }
            case 5 -> {
                int price = rand(4, 30) * 10;
                int p = new int[] {10, 20, 25, 50}[random.nextInt(4)];
                if (price * p % 100 != 0) price = price / 20 * 20 + 20;
                int off = price * p / 100;
                return num(id, Topic.PERCENT, "חולצה עולה " + price + " ש״ח, ויש הנחה של " + p + "%. כמה משלמים?", price - off,
                        List.of(off, price - p, price + off, price - off - 10),
                        "קודם מחשבים כמה ההנחה (" + p + "% מ-" + price + "), ואז מורידים אותה מהמחיר.",
                        "ההנחה: " + off + " ש״ח, ומשלמים " + price + " - " + off + " = " + (price - off) + " ש״ח.", null).withSkill("PCT_DISCOUNT");
            }
            default -> {
                int whole, pct;
                do {
                    whole = new int[] {20, 25, 40, 50, 200}[random.nextInt(5)];
                    pct = new int[] {10, 20, 25, 40, 50, 60, 75, 80}[random.nextInt(8)];
                } while (whole * pct % 100 != 0);
                int part = whole * pct / 100;
                return num(id, Topic.PERCENT, "בכיתה " + whole + " ילדים, ו-" + part + " מהם הגיעו באופניים. כמה אחוז הגיעו באופניים?", pct,
                        List.of(part, 100 - pct, pct + 10, pct / 2 == 0 ? pct + 5 : pct / 2),
                        "כותבים את השבר " + part + "/" + whole + " ומחפשים שבר שווה עם 100 למטה.",
                        part + "/" + whole + " = " + pct + "/100 = " + pct + "%.", null).withSkill("PCT_WHAT_PERCENT");
            }
        }
    }

    // ---- 9. יחס (ו׳) ----------------------------------------------------------------------

    private QuestionResponse ratio(int id, int level) {
        switch (level) {
            case 1 -> {
                int a = rand(1, 4), b = rand(2, 5), k = rand(2, small ? 4 : 8);
                if (a == b) b++;
                return num(id, Topic.RATIO, "בגינה על כל " + a + " ורדים יש " + b + " חמניות. יש " + a * k + " ורדים. כמה חמניות?", b * k,
                        List.of(a * k + (b - a), b + k, a * k, b * k + b, b * (k - 1), b * k - b / 2 - 1),
                        "בודקים כמה פעמים " + a + " נכנס ב-" + a * k + ", ומכפילים גם את " + b + " באותו מספר.",
                        a * k + " = " + a + " × " + k + ", ולכן " + b + " × " + k + " = " + b * k + " חמניות.", null).withSkill("RATIO_SCALE");
            }
            case 2 -> {
                int a = rand(1, 5), b = rand(2, 7);
                int g = FractionGeometryQuestions.gcd(a, b);
                a /= g; b /= g;
                if (a == b) b++;
                int k = rand(2, 6);
                String right = a + " : " + b;
                return text(id, Topic.RATIO, "צמצמו את היחס " + a * k + " : " + b * k, right,
                        List.of(b + " : " + a, (a + 1) + " : " + b, a * k + " : " + b, a + " : " + (b + 1)),
                        "מחלקים את שני המספרים באותו מספר, עד שאי אפשר יותר.",
                        a * k + " : " + b * k + " (חילקנו ב-" + k + ") = " + right + ".", null).withSkill("RATIO_SIMPLIFY");
            }
            case 3 -> {
                int eggs = rand(2, 4), people = rand(2, 4), k = rand(2, 4);
                return num(id, Topic.RATIO, "במתכון ל-" + people + " אנשים צריך " + eggs + " ביצים. כמה ביצים צריך ל-" + people * k + " אנשים?", eggs * k,
                        List.of(eggs + people * k - people, eggs * k + eggs, people * k, eggs + k, eggs * (k - 1), eggs * k - 1),
                        "פי כמה יותר אנשים? פי כך יותר ביצים.", people * k + " = פי " + k + ", ולכן " + eggs + " × " + k + " = " + eggs * k + ".", null).withSkill("RATIO_RECIPE");
            }
            case 4 -> {
                int a = rand(1, 4), b = rand(2, 5), k = rand(2, 8);
                if (a == b) b++;
                int total = (a + b) * k;
                return num(id, Topic.RATIO, "דנה ויואב חילקו " + total + " קלפים ביחס " + a + " : " + b + " (דנה : יואב). כמה קיבלה דנה?", a * k,
                        List.of(b * k, total / 2, total / a == a * k ? a * k + k : total / (a + b), a * k + a),
                        "יש " + (a + b) + " חלקים בסך הכל. כמה קלפים בחלק אחד?",
                        total + " : " + (a + b) + " = " + k + " בכל חלק, ולדנה " + a + " חלקים: " + a * k + ".", null).withSkill("RATIO_SHARE");
            }
            case 5 -> {
                int cm = rand(2, 9);
                int scale = new int[] {100, 1000}[random.nextInt(2)];
                int meters = cm * scale / 100;
                return num(id, Topic.RATIO, "במפה בקנה מידה 1 : " + scale + ", מרחק של " + cm + " ס״מ במפה הוא כמה מטרים באמת?", meters,
                        List.of(cm * scale, meters * 10, cm, meters / 10 == 0 ? meters + 100 : meters / 10),
                        "כל ס״מ במפה הוא " + scale + " ס״מ באמת. אחר כך הופכים לסנטימטרים למטרים (מחלקים ב-100).",
                        cm + " × " + scale + " = " + cm * scale + " ס״מ = " + meters + " מטר.", null).withSkill("RATIO_MAP");
            }
            default -> {
                int speed = rand(4, 9) * 10, h = rand(2, 5);
                return num(id, Topic.RATIO, "מכונית נוסעת " + speed + " קמ״ש. כמה קילומטרים תעבור ב-" + h + " שעות?", speed * h,
                        List.of(speed + h, speed * h + speed, speed * (h - 1), speed * h - 10, speed * h + 10, speed * (h - 2) + 5),
                        "קמ״ש = כמה קילומטרים בשעה אחת. כמה שעות? כופלים.",
                        speed + " × " + h + " = " + speed * h + " ק״מ.", null).withSkill("RATIO_SPEED");
            }
        }
    }

    // ---- 10. נתונים, גרפים, ממוצע וסיכוי (ב׳-ו׳) -----------------------------------------------

    private static final String[][] CHART_SETS = {
            {"הפרי האהוב", "תפוח", "בננה", "ענבים", "תות"},
            {"חיית המחמד האהובה", "כלב", "חתול", "דג", "ארנב"},
            {"הספורט האהוב", "כדורגל", "כדורסל", "שחייה", "ריצה"},
            {"הצבע האהוב", "כחול", "אדום", "ירוק", "סגול"},
    };

    private QuestionResponse data(int id, int level) {
        String[] set = CHART_SETS[random.nextInt(CHART_SETS.length)];
        int n = 4;
        int maxV = grade.ordinal() <= Grade.G2.ordinal() ? 10 : 20;
        int[] v = new int[n];
        Set<Integer> used = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            int x;
            do { x = rand(1, maxV); } while (!used.add(x));
            v[i] = x;
        }
        StringBuilder chart = new StringBuilder();
        for (int i = 0; i < n; i++) chart.append(i == 0 ? "" : "|").append(set[i + 1]).append(":").append(v[i]);
        VisualModel pic = new VisualModel("BAR_CHART", chart.toString(), maxV, n, 0, set[0]);
        String intro = "בגרף - " + set[0] + " של ילדי הכיתה. ";
        switch (level) {
            case 1 -> {
                int i = random.nextInt(n);
                List<Integer> wrong = new ArrayList<>();
                for (int j = 0; j < n; j++) if (j != i) wrong.add(v[j]);
                wrong.add(v[i] + 1);
                return num(id, Topic.DATA, intro + "כמה ילדים בחרו " + set[i + 1] + "?", v[i], wrong,
                        "מוצאים את העמודה של " + set[i + 1] + " ומסתכלים לאיזה מספר היא מגיעה.",
                        "העמודה של " + set[i + 1] + " מגיעה ל-" + v[i] + ".", pic).withSkill("DATA_READ");
            }
            case 2 -> {
                boolean most = random.nextBoolean();
                int best = 0;
                for (int j = 1; j < n; j++) if (most ? v[j] > v[best] : v[j] < v[best]) best = j;
                List<String> o = new ArrayList<>();
                for (int j = 0; j < n; j++) o.add(set[j + 1]);
                return new QuestionResponse(id, Topic.DATA, intro + "מה בחרו הכי " + (most ? "הרבה" : "מעט") + " ילדים?", o, best,
                        "מחפשים את העמודה ה" + (most ? "גבוהה" : "נמוכה") + " ביותר.",
                        set[best + 1] + " - " + v[best] + " ילדים.", pic).withSkill("DATA_MOST");
            }
            case 3 -> {
                int i = random.nextInt(n), j = (i + 1 + random.nextInt(n - 1)) % n;
                int hi = v[i] > v[j] ? i : j, lo = hi == i ? j : i;
                int d = v[hi] - v[lo];
                return num(id, Topic.DATA, intro + "בכמה ילדים יותר בחרו " + set[hi + 1] + " מאשר " + set[lo + 1] + "?", d,
                        List.of(v[hi] + v[lo], v[hi], d + 1, v[lo]),
                        "מוצאים את שני המספרים בגרף ומחסרים: הגדול פחות הקטן.",
                        v[hi] + " - " + v[lo] + " = " + d + ".", pic).withSkill("DATA_DIFF");
            }
            case 4 -> {
                int total = 0;
                for (int x : v) total += x;
                return num(id, Topic.DATA, intro + "כמה ילדים ענו בסך הכל?", total,
                        List.of(total - v[0], total + 10, total - 1, Math.max(Math.max(v[0], v[1]), Math.max(v[2], v[3])) * n),
                        "מחברים את כל העמודות.", "ביחד: " + v[0] + " + " + v[1] + " + " + v[2] + " + " + v[3] + " = " + total + ".", pic).withSkill("DATA_TOTAL");
            }
            case 5 -> {
                // average of 4-5 numbers with an integer result
                int count = rand(3, 5);
                int avg = rand(4, small ? 10 : 30);
                int[] xs = new int[count];
                int sum = 0;
                for (int k = 0; k < count - 1; k++) { xs[k] = Math.max(1, avg + rand(-avg / 2, avg / 2)); sum += xs[k]; }
                xs[count - 1] = avg * count - sum;
                if (xs[count - 1] <= 0) { for (int k = 0; k < count; k++) xs[k] = avg; xs[0] = avg - 1; xs[1] = avg + 1; }
                sum = 0;
                StringBuilder s = new StringBuilder();
                for (int k = 0; k < count; k++) { sum += xs[k]; s.append(k == 0 ? "" : ", ").append(xs[k]); }
                int max = Integer.MIN_VALUE;
                for (int x : xs) max = Math.max(max, x);
                return num(id, Topic.DATA, "נועה קיבלה בשבוע נקודות: " + s + ". מה הממוצע?", avg,
                        List.of(sum, avg + 1, max == avg ? avg + 2 : max, sum / (count + 1) == avg ? avg - 2 : sum / (count + 1)),
                        "ממוצע = מחברים את כל המספרים ומחלקים בכמה מספרים יש.",
                        "סכום: " + sum + ", ויש " + count + " מספרים: " + sum + " : " + count + " = " + avg + ".", null).withSkill("DATA_AVERAGE");
            }
            default -> {
                // chance: a bag with coloured balls
                int red = rand(1, 6), blue = rand(1, 6), green = rand(1, 5);
                while (blue == red) blue = rand(1, 7);
                int total = red + blue + green;
                if (random.nextBoolean()) {
                    List<Integer> counts = distinctInts(3, 1, 8);
                    red = counts.get(0); blue = counts.get(1); green = counts.get(2);
                    List<String> o = new ArrayList<>(List.of("אדום", "כחול", "ירוק"));
                    int best = red > blue && red > green ? 0 : blue > green ? 1 : 2;
                    return new QuestionResponse(id, Topic.DATA, "בשקית " + red + " כדורים אדומים, " + blue + " כחולים ו-" + green + " ירוקים. מוציאים כדור בלי להסתכל. איזה צבע הכי סביר?",
                            o, best, "הצבע שיש ממנו הכי הרבה כדורים - הכי סביר לצאת.",
                            "הכי הרבה כדורים הם בצבע " + o.get(best) + " (" + Math.max(red, Math.max(blue, green)) + "), ולכן הוא הכי סביר.", null).withSkill("DATA_LIKELY");
                }
                String right = red + "/" + total;
                return text(id, Topic.DATA, "בשקית " + red + " כדורים אדומים, " + blue + " כחולים ו-" + green + " ירוקים. מה הסיכוי להוציא כדור אדום?", right,
                        List.of(red + "/" + (blue + green), "1/" + red, blue + "/" + total, "1/3"),
                        "סיכוי = כמה כדורים מתאימים מתוך כמה כדורים יש בסך הכל.",
                        red + " אדומים מתוך " + total + " כדורים: " + right + ".", null).withSkill("DATA_CHANCE");
            }
        }
    }

    // ---- 11. עיגול (ו׳) --------------------------------------------------------------------

    private QuestionResponse circle(int id, int level) {
        int r = rand(1, small ? 5 : 10);
        VisualModel pic = new VisualModel("CIRCLE", null, r, 0, 0, null);
        switch (level) {
            case 1, 2 -> {
                boolean toD = random.nextBoolean();
                if (toD) return num(id, Topic.CIRCLE, "רדיוס העיגול " + r + " ס״מ. מה הקוטר?", 2 * r,
                        List.of(r, r * r, 2 * r + 2, r + 2), "הקוטר עובר דרך המרכז - הוא פעמיים רדיוס.",
                        "קוטר = 2 × " + r + " = " + 2 * r + " ס״מ.", pic).withSkill("CIRCLE_RD");
                return num(id, Topic.CIRCLE, "קוטר העיגול " + 2 * r + " ס״מ. מה הרדיוס?", r,
                        List.of(2 * r, 4 * r, r + 1, r == 1 ? 3 : r - 1), "הרדיוס - מהמרכז לקצה - חצי מהקוטר.",
                        "רדיוס = " + 2 * r + " : 2 = " + r + " ס״מ.", pic).withSkill("CIRCLE_RD");
            }
            case 3, 4 -> {
                int d = 2 * r;
                String right = dec(314 * d, 100);
                return decNum(id, Topic.CIRCLE, "קוטר העיגול " + d + " ס״מ. מה ההיקף? (π ≈ 3.14)", 314 * d,
                        List.of(314 * r, 314 * r * r, 314 * d * 2, (3 + d) * 100, 314 * (d + 1)), 314,
                        "היקף העיגול = קוטר × π (בערך 3.14).", d + " × 3.14 = " + right + " ס״מ.", pic).withSkill("CIRCLE_PERIMETER");
            }
            default -> {
                String right = dec(314 * r * r, 100);
                return decNum(id, Topic.CIRCLE, "רדיוס העיגול " + r + " ס״מ. מה השטח? (π ≈ 3.14)", 314 * r * r,
                        List.of(314 * 2 * r, 314 * r, 314 * 4 * r * r, r * r * 100, 314 * (r * r + 1)), 314,
                        "שטח העיגול = רדיוס × רדיוס × π.", r + " × " + r + " × 3.14 = " + right + " סמ״ר.", pic).withSkill("CIRCLE_AREA");
            }
        }
    }

    // ---- 12. נפח (ה׳-ו׳) --------------------------------------------------------------------

    private QuestionResponse volume(int id, int level) {
        int a = rand(2, small ? 5 : 10), b = rand(2, small ? 4 : 8), c = rand(2, small ? 4 : 6);
        switch (level) {
            case 1 -> {
                int s = rand(2, small ? 4 : 6);
                return num(id, Topic.VOLUME, "קובייה שצלעה " + s + " ס״מ. מה הנפח שלה (בסמ״ק)?", s * s * s,
                        List.of(s * s * (s - 1), s * s, 3 * s, 12 * s, s * s * (s + 1), 6 * s * s, (s + 1) * (s + 1) * (s + 1)), "נפח = אורך × רוחב × גובה. בקובייה כולם שווים.",
                        s + " × " + s + " × " + s + " = " + s * s * s + " סמ״ק.", new VisualModel("BOX", null, s, s, s, null)).withSkill("VOL_CUBE");
            }
            case 2, 3 -> {
                return num(id, Topic.VOLUME, "תיבה: אורך " + a + ", רוחב " + b + " וגובה " + c + " ס״מ. מה הנפח?", a * b * c,
                        List.of(a * b * (c - 1), (a - 1) * b * c, a * (b - 1) * c, a + b + c, a * b,
                                2 * (a * b + b * c + a * c), a * b * (c + 1), (a + 1) * b * c),
                        "נפח התיבה = אורך × רוחב × גובה (כמה קוביות של סנטימטר נכנסות).",
                        a + " × " + b + " × " + c + " = " + a * b * c + " סמ״ק.", new VisualModel("BOX", null, a, b, c, null)).withSkill("VOL_BOX");
            }
            case 4 -> {
                int v = a * b * c;
                return num(id, Topic.VOLUME, "נפח התיבה " + v + " סמ״ק. האורך " + a + " והרוחב " + b + ". מה הגובה?", c,
                        List.of(v - a * b, v / a, c + 1, a * b), "נפח = אורך × רוחב × גובה, אז הגובה = נפח : (אורך × רוחב).",
                        v + " : (" + a + " × " + b + ") = " + v + " : " + a * b + " = " + c + ".", new VisualModel("BOX", null, a, b, 0, null)).withSkill("VOL_MISSING");
            }
            case 5 -> {
                int s = rand(2, 6);
                return num(id, Topic.VOLUME, "קובייה שצלעה " + s + " ס״מ. מה שטח הפנים שלה (סכום שטחי 6 הפאות)?", 6 * s * s,
                        List.of(s * s * s, 4 * s * s, 6 * s, 12 * s), "לקובייה 6 פאות, וכל פאה ריבוע.",
                        "פאה: " + s + " × " + s + " = " + s * s + ", ו-6 פאות: " + 6 * s * s + " סמ״ר.", new VisualModel("BOX", null, s, s, s, null)).withSkill("VOL_SURFACE");
            }
            default -> {
                int sa = 2 * (a * b + b * c + a * c);
                return num(id, Topic.VOLUME, "תיבה: " + a + " × " + b + " × " + c + " ס״מ. מה שטח הפנים שלה?", sa,
                        List.of(a * b * c, a * b + b * c + a * c, sa + 2 * a * b, 4 * (a + b + c)),
                        "לתיבה 3 זוגות של פאות שוות: " + a + "×" + b + ", " + b + "×" + c + ", " + a + "×" + c + ". כל אחת פעמיים.",
                        "2 × (" + a * b + " + " + b * c + " + " + a * c + ") = " + sa + " סמ״ר.", new VisualModel("BOX", null, a, b, c, null)).withSkill("VOL_SURFACE");
            }
        }
    }

    // ---- 13. משוואות - "מה x?" (ג׳-ו׳) ---------------------------------------------------

    private QuestionResponse equations(int id, int level) {
        int m = small ? 9 : 20;
        int x = rand(2, m);
        String eq;
        List<Integer> wrong;
        String why;
        switch (level) {
            case 1 -> { int b = rand(2, m); eq = "x + " + b + " = " + (x + b); wrong = List.of(x + 2 * b, x + b, x + 1); why = (x + b) + " - " + b + " = " + x; }
            case 2 -> { int b = rand(2, m); eq = "x - " + b + " = " + x; int real = x + b; x = real; wrong = List.of(real - 2 * b, real - b, real + 1); why = (real - b) + " + " + b + " = " + real; }
            case 3 -> { int k = rand(2, 9); eq = k + " × x = " + k * x; wrong = List.of(k * x - k, k * x + k, x + 1); why = k * x + " : " + k + " = " + x; }
            case 4 -> { int k = rand(2, 9); eq = "x ÷ " + k + " = " + x; int real = x * k; x = real; wrong = List.of(real / k + k, real + k, real - k); why = real / k + " × " + k + " = " + real; }
            case 5 -> { int k = rand(2, 6), b = rand(1, 15); eq = k + " × x + " + b + " = " + (k * x + b); wrong = List.of((k * x + b) / k, k * x, x + b); why = "מורידים " + b + ": " + k * x + ", ומחלקים ב-" + k + ": " + x; }
            default -> { int k = rand(2, 6), b = rand(1, 15); eq = k + " × x - " + b + " = " + (k * x - b); wrong = List.of((k * x - b) / k, x - 1 > 0 ? x - 1 : x + 2, k * x); why = "מוסיפים " + b + ": " + k * x + ", ומחלקים ב-" + k + ": " + x; }
        }
        // "מצאו את x במשוואה:" - not "מה x?": in Hebrew the "?" jumped to the left of the x ("?x מה")
        return num(id, Topic.EQUATIONS, "מצאו את x במשוואה: " + eq, x, wrong,
                "x הוא מספר שמסתתר. עושים את הפעולה ההפוכה: חיבור ↔ חיסור, כפל ↔ חילוק.",
                why + ", ולכן x = " + x + ".", null).withSkill(level <= 2 ? "EQ_ADD" : level <= 4 ? "EQ_MUL" : "EQ_TWO_STEP");
    }

    // ---- helpers -----------------------------------------------------------------------------

    private List<Integer> distinctInts(int count, int min, int max) {
        Set<Integer> s = new LinkedHashSet<>();
        while (s.size() < count) s.add(rand(min, max));
        return new ArrayList<>(s);
    }

    private int rand(int min, int max) {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    /**
     * A numeric question: the right answer + three of the wrong ones (the real mistakes first), so that
     * the right answer is randomly the smallest, 2nd, 3rd or biggest - "pick the middle" doesn't work.
     */
    private QuestionResponse num(int id, Topic topic, String text, int correct, List<Integer> wrong,
                                 String hint, String explanation, VisualModel visual) {
        List<Integer> below = new ArrayList<>(), above = new ArrayList<>();
        for (int w : new LinkedHashSet<>(wrong)) {
            if (w < 0 || w == correct) continue;
            (w < correct ? below : above).add(w);
        }
        for (int extra = 1; below.size() + above.size() < 6 && extra < 40; extra++) {
            int up = correct + extra * (correct >= 20 ? 2 : 1), dn = correct - extra * (correct >= 20 ? 2 : 1);
            if (!above.contains(up)) above.add(up);
            if (dn >= 0 && !below.contains(dn) && below.size() + above.size() < 6) below.add(dn);
        }
        // keep the real mistakes (they're first) but choose how many go below at random
        int lo = Math.max(0, 3 - above.size()), hi = Math.min(3, below.size());
        int k = lo + random.nextInt(Math.max(1, hi - lo + 1));
        k = Math.min(k, hi);
        List<Integer> out = new ArrayList<>(below.subList(0, k));
        out.addAll(above.subList(0, Math.min(3 - k, above.size())));
        out.add(correct);
        Collections.shuffle(out, random);
        List<String> opts = out.stream().map(String::valueOf).toList();
        return new QuestionResponse(id, topic, text, new ArrayList<>(opts), opts.indexOf(String.valueOf(correct)), hint, explanation, visual);
    }

    /**
     * Like {@link #num} for decimals: values in hundredths (1256 = 12.56), the right answer placed at a
     * random rank among the wrong ones; topped up with right ± step.
     */
    private QuestionResponse decNum(int id, Topic topic, String text, int correct, List<Integer> wrong, int step,
                                    String hint, String explanation, VisualModel visual) {
        List<Integer> below = new ArrayList<>(), above = new ArrayList<>();
        for (int w : new LinkedHashSet<>(wrong)) {
            if (w < 0 || w == correct) continue;
            (w < correct ? below : above).add(w);
        }
        for (int extra = 1; extra < 6; extra++) {
            if (!above.contains(correct + extra * step)) above.add(correct + extra * step);
            if (correct - extra * step > 0 && !below.contains(correct - extra * step)) below.add(correct - extra * step);
        }
        int lo = Math.max(0, 3 - above.size()), hi = Math.min(3, below.size());
        int k = Math.min(hi, lo + random.nextInt(Math.max(1, hi - lo + 1)));
        Collections.shuffle(below, random);
        Collections.shuffle(above, random);
        // the real mistakes stay in: put them first again after shuffling
        below.sort((x, y) -> Boolean.compare(!wrong.contains(x), !wrong.contains(y)));
        above.sort((x, y) -> Boolean.compare(!wrong.contains(x), !wrong.contains(y)));
        List<Integer> out = new ArrayList<>(below.subList(0, k));
        out.addAll(above.subList(0, 3 - k));
        out.add(correct);
        Collections.shuffle(out, random);
        List<String> o = out.stream().map(x -> dec(x, 100)).toList();
        return new QuestionResponse(id, topic, text, new ArrayList<>(o), o.indexOf(dec(correct, 100)), hint, explanation, visual);
    }

    /** A question with text answers: the right one + up to three distinct wrong ones (topped up if needed). */
    private QuestionResponse text(int id, Topic topic, String text, String correct, List<String> wrong,
                                  String hint, String explanation, VisualModel visual) {
        Set<String> options = new LinkedHashSet<>();
        options.add(correct);
        for (String w : wrong) {
            if (options.size() < 4 && w != null && !w.isBlank() && !w.equals(correct) && !w.contains("/0") && !w.startsWith("-")) options.add(w);
        }
        int bump = 1;
        java.math.BigDecimal asNumber = null;
        try { asNumber = new java.math.BigDecimal(correct); } catch (NumberFormatException ignored) {}
        while (options.size() < 4) {
            if (asNumber != null) options.add(asNumber.add(java.math.BigDecimal.valueOf(bump++)).stripTrailingZeros().toPlainString());
            else if (correct.contains("/")) options.add((Integer.parseInt(correct.split("/")[0].trim()) + bump++) + "/" + correct.split("/")[1]);
            else options.add(correct + " (" + bump++ + ")"); // not reached by the generators (checked in ExtraTopicsTest)
        }
        List<String> o = new ArrayList<>(options);
        Collections.shuffle(o, random);
        return new QuestionResponse(id, topic, text, o, o.indexOf(correct), hint, explanation, visual);
    }
}
