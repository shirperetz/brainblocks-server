package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Topic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-11-12 (Shir, stage 2/B: "know WHERE the mistake comes from"): what kind of mistake a wrong
 * answer is - a forgotten carry, the neighbouring row of the times table, adding the denominators,
 * area instead of perimeter... Pure code, no AI: the question's own words and numbers are read back
 * and the wrong answer is compared with what each known mistake would have given.
 * <p>
 * It works from what is already saved with every answer (question text, the child's answer, the right
 * one), so old answers get a label too, and nothing new is ever sent to the child's phone (labelling the
 * options when the question is made would have to be hidden from the client - here there is nothing to
 * hide). A wrong answer nothing explains is {@link #UNKNOWN} - never shown as a conclusion.
 */
public final class Mistakes {

    private Mistakes() {}

    public static final String CARRY = "CARRY";
    public static final String PLACE_VALUE = "PLACE_VALUE";
    public static final String TIMES_TABLE = "TIMES_TABLE";
    public static final String COUNTING = "COUNTING";
    public static final String WRONG_OPERATION = "WRONG_OPERATION";
    public static final String SEQUENCE_STEP = "SEQUENCE_STEP";
    public static final String REVERSED = "REVERSED";
    public static final String FRACTION_MEANING = "FRACTION_MEANING";
    public static final String ADD_DENOMINATORS = "ADD_DENOMINATORS";
    public static final String NO_COMMON_DENOMINATOR = "NO_COMMON_DENOMINATOR";
    public static final String PART_WHOLE = "PART_WHOLE";
    public static final String INCOMPLETE = "INCOMPLETE";
    public static final String DECIMAL_PLACE = "DECIMAL_PLACE";
    public static final String AREA_PERIMETER = "AREA_PERIMETER";
    public static final String WRONG_MEASURE = "WRONG_MEASURE";
    public static final String ANGLE_SUM = "ANGLE_SUM";
    public static final String ANGLE_TYPE = "ANGLE_TYPE";
    public static final String SHAPE_NAMES = "SHAPE_NAMES";
    public static final String UNKNOWN = "UNKNOWN";

    /** For the teacher / parent - what probably goes wrong, and what to practise. */
    private static final Map<String, String[]> INFO = new LinkedHashMap<>();
    static {
        INFO.put(CARRY, new String[] {"שוכח/ת את הנשיאה או הפריטה (התשובה יוצאת 10 יותר או פחות)",
                "לתרגל תרגילים במאונך ולסמן את הנשיאה מעל העמודה."});
        INFO.put(PLACE_VALUE, new String[] {"מתבלבל/ת בערך המקום (אחדות, עשרות, מאות)",
                "לפרק מספרים לעשרות ואחדות (47 = 40 + 7) ולהשוות ספרה מול ספרה משמאל."});
        INFO.put(TIMES_TABLE, new String[] {"עובדות לוח הכפל לא יציבות (שורה שכנה בלוח)",
                "לחזור על לוח הכפל - שורה אחת בכל פעם, בקול."});
        INFO.put(COUNTING, new String[] {"טעות קטנה בחישוב או בספירה (אחד-שניים ליד)",
                "לבדוק את התשובה פעם נוספת לפני הלחיצה; טעות כזאת לבד היא לא קושי בהבנה."});
        INFO.put(WRONG_OPERATION, new String[] {"בוחר/ת פעולה לא נכונה (חיבור במקום חיסור, כפל במקום חיבור...)",
                "לשאול לפני שמחשבים: מה קורה בסיפור - מוסיפים, מורידים או קבוצות שוות?"});
        INFO.put(SEQUENCE_STEP, new String[] {"לא מוצא/ת את הקפיצה הנכונה בסדרה",
                "לכתוב מתחת לסדרה כמה מוסיפים או מורידים בין כל שני מספרים."});
        INFO.put(REVERSED, new String[] {"הופך/ת בין המספר למעלה למספר למטה",
                "למעלה - כמה לקחנו (או מה מחלקים), למטה - לכמה חלקים חילקנו (או במה מחלקים)."});
        INFO.put(FRACTION_MEANING, new String[] {"עוד לא ברור מה אומר כל מספר בשבר",
                "לצייר עיגול או מלבן, לחלק לחלקים שווים ולצבוע - ולכתוב את השבר מהציור."});
        INFO.put(ADD_DENOMINATORS, new String[] {"מחבר/ת גם את המספרים למטה (המכנים)",
                "כשהמכנה שווה - החלקים באותו גודל, ורק סופרים כמה חלקים יש: המכנה נשאר."});
        INFO.put(NO_COMMON_DENOMINATOR, new String[] {"מחבר/ת שברים בלי להביא קודם למכנה משותף",
                "קודם להפוך את השברים לאותו מכנה, ורק אחר כך לחבר."});
        INFO.put(PART_WHOLE, new String[] {"מתבלבל/ת בין החלק לבין מה שנשאר (או השלם)",
                "לקרוא שוב את השאלה: שואלים כמה נתנו, כמה נשאר או כמה יש בסך הכל?"});
        INFO.put(INCOMPLETE, new String[] {"עוצר/ת באמצע הדרך (שלב אחד חסר)",
                "לכתוב את השלבים אחד מתחת לשני, ולבדוק בסוף שהתשובה עונה על השאלה."});
        INFO.put(DECIMAL_PLACE, new String[] {"מתבלבל/ת במקום הנקודה העשרונית",
                "0.3 זה 3 עשיריות: לחשב בעשיריות ורק בסוף לכתוב עם נקודה."});
        INFO.put(AREA_PERIMETER, new String[] {"מתבלבל/ת בין שטח להיקף",
                "היקף = הגדר מסביב (מחברים צלעות), שטח = כמה ריבועים בפנים (כופלים)."});
        INFO.put(WRONG_MEASURE, new String[] {"משתמש/ת במידה הלא נכונה (צלע משופעת במקום גובה)",
                "לסמן בציור את הגובה - הקו הישר מהבסיס למעלה."});
        INFO.put(ANGLE_SUM, new String[] {"מתבלבל/ת בסכום הזוויות (180° / 360° / 90°)",
                "קו ישר ומשולש = 180°, סיבוב שלם ומרובע = 360°, זווית ישרה = 90°."});
        INFO.put(ANGLE_TYPE, new String[] {"מתבלבל/ת בסוגי הזוויות והמשולשים",
                "להשוות כל זווית לפינה של דף (90°): קטנה - חדה, שווה - ישרה, גדולה - קהה."});
        INFO.put(SHAPE_NAMES, new String[] {"שמות הצורות עוד לא יציבים",
                "שם הצורה בא מהמספר: מחומש - 5, משושה - 6, מתומן - 8."});
    }

    public static String label(String type) {
        String[] i = INFO.get(type);
        return i == null ? "" : i[0];
    }

    public static String tip(String type) {
        String[] i = INFO.get(type);
        return i == null ? "" : i[1];
    }

    public static boolean known(String type) {
        return type != null && INFO.containsKey(type);
    }

    // ---------------------------------------------------------------------------------------------

    private static final String N = "(\\d+)";
    private static final Pattern PLAIN = Pattern.compile("כמה זה " + N + " ([+\\-×÷]) " + N + "\\?");
    private static final Pattern MISSING = Pattern.compile("מה המספר החסר\\? (\\?|\\d+) ([+\\-×÷]) (\\?|\\d+) = " + N);
    private static final Pattern SEQUENCE = Pattern.compile("מה המספר הבא\\? " + N + ", " + N + ",");
    private static final Pattern FRAC = Pattern.compile(N + "/" + N);
    private static final Pattern NUM = Pattern.compile("\\d+");

    /**
     * The kind of mistake [given] is for this question, or UNKNOWN. A right answer (or no answer) is
     * null. Never throws: a question it doesn't recognise is UNKNOWN.
     */
    public static String classify(Topic topic, String text, String correct, String given) {
        if (text == null || correct == null || given == null) return null;
        String c = correct.strip(), g = given.strip();
        if (g.isEmpty() || c.equals(g)) return null;
        try {
            String t = text.strip();
            String r = byText(t, c, g);
            if (r != null) return r;
            // The 60-second race / mixed rounds use the same arithmetic texts; anything else numeric:
            Integer ci = integer(c), gi = integer(g);
            if (ci != null && gi != null && Math.abs(ci - gi) <= 2) return COUNTING;
        } catch (RuntimeException ignored) {
            // an odd text - no label rather than a wrong one
        }
        return UNKNOWN;
    }

    private static String byText(String t, String c, String g) {
        Matcher m;
        if ((m = PLAIN.matcher(t)).matches()) {
            return arithmetic(Integer.parseInt(m.group(1)), m.group(2), Integer.parseInt(m.group(3)), c, g);
        }
        if ((m = MISSING.matcher(t)).find()) return missing(m, c, g);
        if ((m = SEQUENCE.matcher(t)).find()) return sequence(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), c, g);
        if (t.startsWith("איזה המספר")) return PLACE_VALUE; // הגדול / הקטן ביותר: comparing by place value
        if (t.startsWith("ל") && (t.contains("הלכו לאיבוד") || t.contains("שקיות") || t.contains("וקיבל"))) return wordProblem(t, c, g);
        String f = fractions(t, c, g);
        if (f != null) return f;
        return geometry(t, c, g);
    }

    // ---- arithmetic --------------------------------------------------------------------------

    private static String arithmetic(int a, String op, int b, String c, String g) {
        Integer gi = integer(g), ci = integer(c);
        if (gi == null || ci == null) return UNKNOWN;
        if (otherOperation(a, op, b, gi)) return WRONG_OPERATION;
        if (op.equals("-") && gi == digitwiseDiff(a, b)) return CARRY; // "the smaller digit from the bigger" - no borrowing
        if (op.equals("+") && gi == digitwiseSum(a, b)) return CARRY;  // the carry written down, not carried
        return distance(op, a, b, ci, gi);
    }

    /** What the child's answer is far from the right one by - the same for "כמה זה" and "מה חסר". */
    private static String distance(String op, int a, int b, int ci, int gi) {
        int d = Math.abs(gi - ci);
        if (op.equals("×")) {
            if (d == a || d == b) return TIMES_TABLE;
            if (d <= 2) return COUNTING;
            if (d % 10 == 0) return CARRY;
            return UNKNOWN;
        }
        if (op.equals("÷")) return d <= 3 ? TIMES_TABLE : UNKNOWN;
        if (d == 10) return CARRY;
        if (d == 20 || d == 100 || d == 1000) return PLACE_VALUE;
        if (d <= 3) return COUNTING;
        return UNKNOWN;
    }

    /** [g] is what another of + - × ÷ gives on the same two numbers. */
    private static boolean otherOperation(int a, String op, int b, int g) {
        for (String o : List.of("+", "-", "×", "÷")) {
            if (o.equals(op)) continue;
            Integer v = apply(a, o, b);
            if (v != null && v == g) return true;
        }
        return false;
    }

    private static Integer apply(int a, String op, int b) {
        return switch (op) {
            case "+" -> a + b;
            case "-" -> Math.abs(a - b);
            case "×" -> a * b;
            default -> b != 0 && a % b == 0 ? a / b : null;
        };
    }

    /** 52 - 17 "= 45": in every column the smaller digit is taken from the bigger one. */
    static int digitwiseDiff(int a, int b) {
        int out = 0, place = 1;
        while (a > 0 || b > 0) {
            out += Math.abs(a % 10 - b % 10) * place;
            a /= 10;
            b /= 10;
            place *= 10;
        }
        return out;
    }

    /** 47 + 38 "= 715": each column's sum written as it is. */
    static int digitwiseSum(int a, int b) {
        StringBuilder s = new StringBuilder();
        while (a > 0 || b > 0) {
            s.insert(0, a % 10 + b % 10);
            a /= 10;
            b /= 10;
        }
        try {
            return s.isEmpty() ? 0 : Integer.parseInt(s.toString());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String missing(Matcher m, String c, String g) {
        Integer gi = integer(g), ci = integer(c);
        if (gi == null || ci == null) return UNKNOWN;
        String op = m.group(2);
        int r = Integer.parseInt(m.group(4));
        int known = Integer.parseInt(m.group(1).equals("?") ? m.group(3) : m.group(1));
        // "? + 5 = 12" answered 17: the same operation on the numbers shown instead of undoing it.
        if (otherOperation(r, "", known, gi)) return WRONG_OPERATION;
        int a = m.group(1).equals("?") ? ci : known, b = m.group(1).equals("?") ? known : ci;
        // × / ÷ with a missing factor: one, two, three away = a times-table fact.
        return distance(op.equals("×") ? "÷" : op, a, b, ci, gi);
    }

    private static String sequence(int t1, int t2, String c, String g) {
        Integer gi = integer(g), ci = integer(c);
        if (gi == null || ci == null) return UNKNOWN;
        int step = Math.abs(t2 - t1), d = Math.abs(gi - ci);
        if (step > 0 && d % step == 0 && d <= 2 * step) return SEQUENCE_STEP;
        if (d <= 2) return COUNTING;
        return UNKNOWN;
    }

    private static String wordProblem(String t, String c, String g) {
        List<Integer> n = numbers(t);
        Integer gi = integer(g), ci = integer(c);
        if (n.size() < 2 || gi == null || ci == null) return UNKNOWN;
        int a = n.get(0), b = n.get(1);
        String op = t.contains("הלכו לאיבוד") ? "-" : t.contains("שקיות") ? "×" : "+";
        if (otherOperation(a, op, b, gi)) return WRONG_OPERATION;
        return distance(op, a, b, ci, gi);
    }

    // ---- fractions ---------------------------------------------------------------------------

    private static String fractions(String t, String c, String g) {
        List<int[]> fr = fracs(t);
        int[] cf = frac(c), gf = frac(g);
        Integer ci = integer(c), gi = integer(g);

        if (t.startsWith("איך כותבים") && t.contains("כמספר עשרוני")) return DECIMAL_PLACE;
        if (t.startsWith("איך כותבים") || t.startsWith("איך קוראים לשבר")) {
            if (cf != null && gf != null && cf[0] == gf[1] && cf[1] == gf[0]) return REVERSED;
            return FRACTION_MEANING;
        }
        if (t.matches("^0\\.\\d \\+ 0\\.\\d = \\?$")) return g.contains(".") && !g.matches("0\\.\\d") || !g.contains(".") ? DECIMAL_PLACE : COUNTING;
        if (t.startsWith("כמה שלמים הם") && fr.size() == 1 && gi != null && ci != null) {
            int[] f = fr.get(0);
            if (gi == f[1]) return FRACTION_MEANING;
            if (gi == f[0] - f[1]) return WRONG_OPERATION;
            return Math.abs(gi - ci) <= 2 ? COUNTING : UNKNOWN;
        }
        if (t.startsWith("כמה שלמים יש ב") && fr.size() == 1) {
            int d = fr.get(0)[1];
            List<Integer> cn = numbersOrOne(c), gn = numbersOrOne(g);
            if (cn.size() == 2 && gn.size() == 2) {
                if (gn.get(1) >= d) return INCOMPLETE;
                if (gn.get(0).equals(cn.get(1)) && gn.get(1).equals(cn.get(0))) return REVERSED;
                return COUNTING;
            }
            return UNKNOWN;
        }
        if (t.startsWith("כתבו את התרגיל") && cf != null && gf != null) {
            if (gf[0] == cf[1] && gf[1] == cf[0]) return REVERSED;
            if (gf[1] == cf[0] + cf[1]) return PART_WHOLE;
            return COUNTING;
        }
        if (t.startsWith("כתבו את השבר") && fr.size() == 1) {
            int[] f = fr.get(0);
            if (g.equals(f[1] + " : " + f[0])) return REVERSED;
            if (g.contains("×") || g.contains("-")) return WRONG_OPERATION;
            if (g.equals(f[0] + " : " + (f[0] + f[1]))) return PART_WHOLE;
            return UNKNOWN;
        }
        if (t.startsWith("התוצאה של")) return FRACTION_MEANING;
        if (t.startsWith("איזה מספר חסר?") && fr.size() == 1 && gi != null && ci != null) {
            int[] f = fr.get(0);
            int bigD = numbers(t.substring(t.indexOf("?/") + 2)).get(0);
            int k = f[1] == 0 ? 0 : bigD / f[1];
            if (gi == f[0] + k || gi == bigD - f[0] || gi == f[0] + (bigD - f[1])) return WRONG_OPERATION;
            if (gi == ci + f[0]) return TIMES_TABLE;
            return Math.abs(gi - ci) <= 2 ? COUNTING : UNKNOWN;
        }
        if (t.startsWith("צמצמו") && fr.size() == 1 && cf != null && gf != null) {
            if ((long) gf[0] * cf[1] == (long) gf[1] * cf[0]) return INCOMPLETE; // equal, but not all the way
            if (gf[0] - cf[0] == gf[1] - cf[1] && gf[0] != cf[0]) return WRONG_OPERATION;
            return FRACTION_MEANING;
        }
        if (t.startsWith("ל") && t.contains("נתנו לחבר")) {
            List<Integer> n = numbers(t);
            if (gi != null && ci != null && !n.isEmpty()) {
                int whole = n.get(0);
                if (gi == whole - ci) return PART_WHOLE;
                return Math.abs(gi - ci) <= 5 ? COUNTING : UNKNOWN;
            }
            return UNKNOWN;
        }
        if (t.startsWith("איזה חלק") && t.contains("צבוע") && cf != null && gf != null) {
            if (gf[0] == cf[1] - cf[0] && gf[1] == cf[1]) return PART_WHOLE; // counted the white parts
            if (gf[0] == cf[1] && gf[1] == cf[0]) return REVERSED;
            if (gf[1] == cf[0] + cf[1]) return PART_WHOLE;
            return COUNTING;
        }
        if (t.startsWith("איזה שבר הכי גדול") || t.startsWith("איזה שבר שווה")) {
            if (t.startsWith("איזה שבר שווה") && fr.size() == 1 && gf != null) {
                int[] f = fr.get(0);
                if (gf[0] - f[0] == gf[1] - f[1] && gf[0] > f[0]) return WRONG_OPERATION; // added instead of multiplied
                return INCOMPLETE;
            }
            return FRACTION_MEANING;
        }
        if (fr.size() == 2 && t.endsWith("= ?") && cf != null && gf != null) {
            int[] x = fr.get(0), y = fr.get(1);
            boolean plus = t.contains(" + ");
            if (gf[1] == x[1] + y[1]) return ADD_DENOMINATORS;
            if (x[1] == y[1]) {
                if (!plus && gf[0] == x[0] + y[0] && gf[1] == x[1]) return WRONG_OPERATION;
                return COUNTING;
            }
            if (gf[0] == x[0] + y[0] && (gf[1] == x[1] || gf[1] == y[1])) return NO_COMMON_DENOMINATOR;
            return COUNTING;
        }
        if (t.startsWith("כמה זה") && fr.size() == 1 && gi != null && ci != null) { // "כמה זה 3/4 מ-20?"
            int[] f = fr.get(0);
            List<Integer> n = numbers(t.substring(t.indexOf(" מ-")));
            if (n.isEmpty()) return UNKNOWN;
            int whole = n.get(0);
            if (f[1] != 0 && gi == whole / f[1] && f[0] != 1) return INCOMPLETE;
            if (gi == whole - ci) return PART_WHOLE;
            return Math.abs(gi - ci) <= 2 ? COUNTING : UNKNOWN;
        }
        return null;
    }

    // ---- geometry ----------------------------------------------------------------------------

    private static String geometry(String t, String c, String g) {
        Integer gi = integer(g.replace("°", "")), ci = integer(c.replace("°", ""));
        List<Integer> n = numbers(t);

        if (t.startsWith("זווית של") || t.startsWith("איזו מהזוויות") || t.startsWith("למשולש יש זוויות")) return ANGLE_TYPE;
        if (t.startsWith("כמה מעלות יש")) return ANGLE_SUM;
        if (t.startsWith("איך קוראים לצורה")) return SHAPE_NAMES;
        if (t.startsWith("כמה צלעות")) return COUNTING;
        if (t.contains("היקף העיגול")) {
            if (n.isEmpty()) return UNKNOWN;
            int d = n.get(0);
            if (g.equals(trim(1.57 * d))) return INCOMPLETE;
            if (g.equals(trim(3.14 * d * d / 4))) return AREA_PERIMETER;
            return COUNTING;
        }
        if (gi == null || ci == null) return null;

        // Angles: every question here is "what is left of a known total".
        if (t.contains("°")) {
            int d = Math.abs(gi - ci);
            if (t.contains("שווה-שוקיים") && !n.isEmpty()) {
                if (gi == 180 - n.get(0)) return INCOMPLETE; // took the known angle(s) from 180, didn't halve / didn't take both
                if (d <= 30 && d % 10 == 0) return COUNTING;
                return ANGLE_SUM;
            }
            if (t.contains("קודקודית") || t.contains("צמודה")) return ANGLE_TYPE;
            if (d == 90 || d == 180 || d == 270) return ANGLE_SUM;
            if (n.size() >= 2 && gi == n.get(0) + n.get(1)) return INCOMPLETE; // added the angles, stopped
            if (n.size() >= 1 && gi.equals(n.get(0))) return ANGLE_SUM;
            if (d <= 30 && d % 10 == 0) return COUNTING;
            return ANGLE_SUM;
        }
        if (t.contains("מקבילית") && n.size() >= 3) {
            int base = n.get(0), side = n.get(1);
            if (gi == base * side) return WRONG_MEASURE;
            if (gi == 2 * (base + side)) return AREA_PERIMETER;
            return TIMES_TABLE;
        }
        if (t.contains("משולש ישר-זווית") && t.contains("השטח") && n.size() >= 2) {
            int a = n.get(0), b = n.get(1);
            if (gi == a * b) return INCOMPLETE; // forgot the half
            if (gi == a + b) return WRONG_OPERATION;
            return COUNTING;
        }
        if (t.startsWith("למשולש יש צלעות") && n.size() >= 3) {
            if (gi == n.get(0) + n.get(1)) return INCOMPLETE;
            if (gi == n.get(0) * n.get(1)) return WRONG_OPERATION;
            return COUNTING;
        }
        if (t.startsWith("השטח של ריבוע") && n.size() >= 1) { // the side from the area
            int area = n.get(0);
            if (gi == area / 4) return AREA_PERIMETER;
            if (gi == area / 2) return WRONG_OPERATION;
            return COUNTING;
        }
        if (t.startsWith("ההיקף של מלבן") && n.size() >= 2) {
            int p = n.get(0), w = n.get(1);
            if (t.contains("השטח")) {
                if (gi == p) return AREA_PERIMETER;
                if (gi == w * (p / 2) || gi == p - w) return INCOMPLETE;
                return COUNTING;
            }
            if (gi == p / 2) return INCOMPLETE; // forgot to take the length away
            return COUNTING;
        }
        if (t.contains("ריבוע שצלעו") && n.size() >= 1) {
            int s = n.get(0);
            boolean area = t.contains("השטח");
            if (area ? gi == 4 * s : gi == s * s) return AREA_PERIMETER;
            if (area) return gi == 2 * s ? WRONG_OPERATION : gi == s * s + s ? TIMES_TABLE : COUNTING;
            if (gi == 2 * s || gi == 3 * s) return INCOMPLETE; // not all four sides
            return COUNTING;
        }
        if (t.contains("מלבן שאורכו") && n.size() >= 2) {
            int w = n.get(0), h = n.get(1);
            boolean area = t.contains("השטח");
            if (area ? gi == 2 * (w + h) : gi == w * h) return AREA_PERIMETER;
            if (area && gi == w + h) return WRONG_OPERATION;
            if (!area && (gi == w + h || gi == 2 * w + h || gi == w + 2 * h)) return INCOMPLETE;
            if (area && (Math.abs(gi - ci) == w || Math.abs(gi - ci) == h)) return TIMES_TABLE;
            return COUNTING;
        }
        return null;
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static Integer integer(String s) {
        return s != null && s.matches("\\d{1,9}") ? Integer.valueOf(s) : null;
    }

    private static int[] frac(String s) {
        Matcher m = Pattern.compile("^" + N + "/" + N + "$").matcher(s);
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    private static List<int[]> fracs(String t) {
        Matcher m = FRAC.matcher(t);
        List<int[]> out = new java.util.ArrayList<>();
        while (m.find()) out.add(new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
        return out;
    }

    private static List<Integer> numbers(String t) {
        Matcher m = NUM.matcher(t);
        List<Integer> out = new java.util.ArrayList<>();
        while (m.find()) {
            try {
                out.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
                // too long - not one of ours
            }
        }
        return out;
    }

    /** "שלם אחד ושארית 2" -> [1, 2]; "3 שלמים ושארית 2" -> [3, 2]. */
    private static List<Integer> numbersOrOne(String s) {
        List<Integer> n = numbers(s);
        if (s.startsWith("שלם אחד")) {
            List<Integer> out = new java.util.ArrayList<>(List.of(1));
            out.addAll(n);
            return out;
        }
        return n;
    }

    private static String trim(double v) {
        String t = String.format(java.util.Locale.US, "%.2f", v);
        if (t.contains(".")) t = t.replaceAll("0+$", "").replaceAll("\\.$", "");
        return t;
    }
}
