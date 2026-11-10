package com.example.brainblocks.learning.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Step-by-step explanations made by code - correct by construction, never the final answer
 * (2026-11-10: the local 8B model wrote "81 - 18 ... 62" and "9, 12, 15" for 9 × 3, so the math
 * itself must not come from it). Two variants per kind, for "תסבירו אחרת". The AI only rephrases
 * these, and may only use the numbers listed in {@link Way#numbers()}.
 */
public final class Tutor {

    /** An explanation + every number it (or a rephrasing of it) may contain. */
    public record Way(String text, Set<Long> numbers) {}

    private static final Pattern ARITH = Pattern.compile("(\\d+)\\s*([+\\-×÷])\\s*(\\d+)");
    private static final Pattern SERIES = Pattern.compile("(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*(\\d+),\\s*\\?");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private Tutor() {}

    /** null when the question isn't one this tutor knows (then the game's own hint is used). */
    public static Way explain(String question, int variant) {
        if (question == null) return null;
        String q = question.trim();
        int v = Math.floorMod(variant, 2);
        Matcher s = SERIES.matcher(q);
        if (s.find()) return series(Long.parseLong(s.group(3)), Long.parseLong(s.group(4)), v, q);
        if (q.contains("המספר הגדול ביותר")) return comparison(v, q);
        Matcher a = ARITH.matcher(q);
        if (a.find()) {
            return arithmetic(Long.parseLong(a.group(1)), a.group(2), Long.parseLong(a.group(3)), v, q);
        }
        return wordProblem(q, v);
    }

    // ---- kinds --------------------------------------------------------------------------------

    private static Way arithmetic(long a, String op, long b, int v, String q) {
        return switch (op) {
            case "+" -> addition(a, b, v, q);
            case "-" -> subtraction(a, b, v, q);
            case "×" -> multiplication(a, b, v, q);
            default -> division(a, b, v, q);
        };
    }

    private static Way addition(long a, long b, int v, String q) {
        long big = Math.max(a, b);
        long small = Math.min(a, b);
        if (a + b <= 20) {
            return v == 0
                    ? way(q, "מתחילים מהמספר הגדול, " + big + ", וסופרים קדימה עוד " + steps(small)
                            + " - צעד על כל אצבע. איפה עצרתם? זאת התשובה.", big, small)
                    : way(q, "דמיינו " + a + " כדורים בקופסה אחת, ועוד " + b
                            + " כדורים בקופסה שנייה. שופכים הכל לקופסה אחת, וסופרים את כל הכדורים.", a, b);
        }
        if (v == 0) {
            return wayOf(q, "מפרקים לפי מקומות: " + split(a) + ", ו-" + split(b)
                    + ". מחברים קודם את האחדות, אחר כך את העשרות, ואחר כך את המאות. בסוף מחברים הכל יחד.",
                    parts(a), parts(b));
        }
        long tens = b - b % 10;
        long ones = b % 10;
        return tens > 0 && ones > 0
                ? wayOf(q, "מוסיפים בשני צעדים: קודם מוסיפים ל-" + a + " את " + tens + ", ואחר כך מוסיפים עוד "
                        + ones + ".", List.of(a, tens, ones))
                : wayOf(q, "מוסיפים בשני צעדים: קודם את המספר הגדול מבין החלקים של " + b + ", ואחר כך את השאר. "
                        + "בכל צעד בודקים את התוצאה.", parts(a), parts(b));
    }

    private static Way subtraction(long a, long b, int v, String q) {
        if (a <= 20) {
            return v == 0
                    ? way(q, "מתחילים מ-" + a + " וסופרים אחורה " + steps(b) + " - צעד על כל אצבע. איפה עצרתם? זאת התשובה.", a, b)
                    : way(q, "אפשר גם הפוך: מתחילים מ-" + b + " וסופרים קדימה עד " + a
                            + ". כמה צעדים עשיתם? זאת התשובה.", a, b);
        }
        if (v == 0) {
            return wayOf(q, "מפרקים לפי מקומות: " + split(a) + ", ו-" + split(b)
                    + ". מחסירים קודם את האחדות, אחר כך את העשרות. אם באחדות אין מספיק, פורטים עשרת אחת לעשר אחדות.",
                    parts(a), parts(b));
        }
        long tens = b - b % 10;
        long ones = b % 10;
        return tens > 0 && ones > 0
                ? wayOf(q, "מורידים בשני צעדים: קודם מורידים מ-" + a + " את " + tens + ", ואחר כך מורידים עוד "
                        + ones + ".", List.of(a, tens, ones))
                : way(q, "אפשר לספור מ-" + b + " קדימה עד " + a + " בקפיצות גדולות של עשרות, ואחר כך באחדות. "
                        + "כמה קפצתם בסך הכל? זאת התשובה.", a, b, 10);
    }

    private static Way multiplication(long a, long b, int v, String q) {
        if (v == 0) {
            String sum = a <= 6 ? " מחברים " + String.join(" ועוד ", java.util.Collections.nCopies((int) a, String.valueOf(b))) + "." : "";
            return way(q, a + " כפול " + b + " זה " + a + " קבוצות, ובכל קבוצה " + b + "." + sum
                    + " כמה יש בכל הקבוצות יחד?", a, b);
        }
        String jumps = a > 2 ? b + ", " + (2 * b) : String.valueOf(b); // never up to the answer itself
        return way(q, "אפשר להפוך את הסדר: " + b + " כפול " + a + " יוצא אותו דבר. בחרו מה קל יותר, "
                + "וספרו בקפיצות. למשל, בקפיצות של " + b + ": " + jumps + ", וכך הלאה, " + a + " קפיצות.",
                a, b, 2 * b);
    }

    private static Way division(long a, long b, int v, String q) {
        if (v == 0) {
            List<Long> jumps = new ArrayList<>();
            long answer = b == 0 ? -1 : a / b;
            // A few jumps, stopping before any that would be the answer itself.
            for (long m = b; m < a && jumps.size() < 3 && (m == b || m != answer); m += b) jumps.add(m);
            String list = jumps.isEmpty() ? "" : " " + String.join(", ", jumps.stream().map(String::valueOf).toList()) + "...";
            List<Long> nums = new ArrayList<>(jumps);
            nums.add(a);
            nums.add(b);
            return wayOf(q, "כמה פעמים " + b + " נכנס בתוך " + a + "? סופרים בקפיצות של " + b + ":" + list
                    + " עד שמגיעים ל-" + a + ", וסופרים כמה קפיצות היו.", nums);
        }
        return way(q, "חושבים בכפל: איזה מספר, כפול " + b + ", נותן " + a + "? מי שיודע את לוח הכפל של " + b
                + " - זה הזמן להשתמש בו.", a, b);
    }

    private static Way series(long t3, long t4, int v, String q) {
        long d = t4 - t3;
        String step = d >= 0 ? "מוסיפים" : "מורידים";
        long ad = Math.abs(d);
        return v == 0
                ? way(q, "בודקים כמה " + step + " בכל פעם: מ-" + t3 + " ל-" + t4 + " " + step + " " + ad
                        + ". עושים את אותו הדבר למספר האחרון, " + t4 + ".", t3, t4, ad)
                : way(q, "בסדרה הזאת כל צעד הוא " + ad + ". מתחילים מ-" + t4 + " ו" + (d >= 0 ? "סופרים קדימה " : "סופרים אחורה ")
                        + steps(ad) + ".", t4, ad);
    }

    private static Way comparison(int v, String q) {
        return way(q, v == 0
                ? "משווים קודם כמה ספרות יש בכל מספר: למספר עם יותר ספרות יש ערך גדול יותר."
                : "אם למספרים יש אותו מספר ספרות, משווים את הספרה הראשונה משמאל. אם היא שווה - עוברים לספרה הבאה.");
    }

    private static Way wordProblem(String q, int v) {
        List<Long> n = numbersIn(q);
        if (n.size() < 2) return null;
        long a = n.get(0);
        long b = n.get(1);
        String ex;
        if (q.contains("הלכו לאיבוד") || q.contains("נשארו")) ex = a + " פחות " + b;
        else if (q.contains("בכל שקית") || q.contains("בכל קופסה")) ex = a + " כפול " + b;
        else if (q.contains("עוד")) ex = a + " ועוד " + b;
        else return null;
        return v == 0
                ? way(q, "מחפשים בסיפור את המספרים, " + a + " ו-" + b + ", ומה קרה להם. כאן התרגיל הוא: " + ex
                        + ". עכשיו פותרים אותו.", a, b)
                : way(q, "מציירים את הסיפור: מציירים את " + a + " הדברים, ואז עושים מה שהסיפור אומר עם " + b
                        + ". סופרים מה יצא בציור.", a, b);
    }

    // ---- helpers ------------------------------------------------------------------------------

    /** "צעד אחד" / "5 צעדים". */
    static String steps(long n) {
        return n == 1 ? "צעד אחד" : n + " צעדים";
    }

    /** "377 = 300 + 70 + 7". */
    static String split(long n) {
        List<Long> p = parts(n);
        if (p.size() <= 1) return String.valueOf(n);
        return n + " = " + String.join(" + ", p.stream().map(String::valueOf).toList());
    }

    static List<Long> parts(long n) {
        List<Long> out = new ArrayList<>();
        String s = Long.toString(n);
        for (int i = 0; i < s.length(); i++) {
            int d = s.charAt(i) - '0';
            if (d != 0) out.add(d * (long) Math.pow(10, s.length() - i - 1));
        }
        if (out.isEmpty()) out.add(0L);
        return out;
    }

    static List<Long> numbersIn(String text) {
        List<Long> out = new ArrayList<>();
        Matcher m = NUMBER.matcher(text == null ? "" : text);
        while (m.find()) {
            if (m.group().length() <= 12) out.add(Long.parseLong(m.group()));
        }
        return out;
    }

    private static Way way(String q, String text, long... extra) {
        List<Long> l = new ArrayList<>();
        for (long x : extra) l.add(x);
        return wayOf(q, text, l);
    }

    @SafeVarargs
    private static Way wayOf(String q, String text, List<Long>... lists) {
        Set<Long> nums = new LinkedHashSet<>(numbersIn(q));
        for (List<Long> l : lists) nums.addAll(l);
        nums.addAll(numbersIn(text));
        return new Way(text, nums);
    }
}
