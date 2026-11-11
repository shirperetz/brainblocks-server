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
        // 2026-11-11: fractions and geometry first - "2/7 + 3/7" must not be read as "7 + 3".
        Way fg = fractionOrGeometry(q, v);
        if (fg != null) return fg;
        Matcher s = SERIES.matcher(q);
        if (s.find()) return series(Long.parseLong(s.group(3)), Long.parseLong(s.group(4)), v, q);
        if (q.contains("המספר הגדול ביותר") || q.contains("המספר הקטן ביותר")) return comparison(v, q);
        if (q.startsWith("מה המספר החסר")) return missingNumber(q, v);
        Matcher a = ARITH.matcher(q);
        if (a.find()) {
            return arithmetic(Long.parseLong(a.group(1)), a.group(2), Long.parseLong(a.group(3)), v, q);
        }
        return wordProblem(q, v);
    }

    // ---- kinds --------------------------------------------------------------------------------

    private static final Pattern FRACTION = Pattern.compile("(\\d+)/(\\d+)");

    /** Ways for שברים / גיאומטריה (FractionGeometryQuestions) - the method, never the answer. */
    static Way fractionOrGeometry(String q, int v) {
        // 2026-11-11: השבר כמנת חילוק and the three geometry tracks - checked first.
        if (q.startsWith("כתבו את התרגיל") && q.contains("כשבר")) {
            return way(q, v == 0
                    ? "קו השבר הוא סימן החילוק. המספר הראשון (זה שמחלקים) נכתב למעלה, והשני (זה שמחלקים בו) למטה."
                    : "קראו את התרגיל משמאל לימין: הראשון למעלה, קו שבר, השני למטה.");
        }
        if (q.startsWith("כתבו את השבר") && q.contains("כתרגיל חילוק")) {
            return way(q, v == 0
                    ? "קו השבר אומר לחלק: את המספר שלמעלה מחלקים במספר שלמטה. כותבים קודם את שלמעלה, אחר כך : ואז את שלמטה."
                    : "שבר = חילוק. המספר שלמעלה הוא הראשון בתרגיל, המספר שלמטה הוא השני.");
        }
        if (q.startsWith("התוצאה של") && q.contains(":")) {
            return way(q, v == 0
                    ? "משווים את שני המספרים: אם הראשון קטן מהשני - התוצאה קטנה מ-1. אם הם שווים - בדיוק 1. אם הראשון גדול - יותר מ-1."
                    : "תחשבו על עוגות: אם מחלקים פחות עוגות מאשר ילדים, כל ילד מקבל פחות מעוגה שלמה.");
        }
        if (q.startsWith("זווית של") && q.contains("היא זווית")) {
            return way(q, v == 0
                    ? "משווים ל-90 מעלות (פינה של דף): פחות - חדה, בדיוק 90 - ישרה, בין 90 ל-180 - קהה, 180 - שטוחה."
                    : "דמיינו פינה של מחברת - זו זווית ישרה של 90°. הזווית שלכם צרה יותר, בדיוק כמוה, או רחבה יותר?");
        }
        if (q.contains("סביב נקודה")) {
            return way(q, v == 0
                    ? "כל הזוויות סביב נקודה יחד הן סיבוב שלם - 360 מעלות. מחסרים מ-360 את הזוויות הידועות."
                    : "חברו קודם את שתי הזוויות הידועות. כמה חסר להן כדי להגיע ל-360?");
        }
        if (q.contains("ההיקף של מלבן") && q.contains("מה השטח")) {
            return way(q, v == 0
                    ? "שני שלבים: קודם מוצאים את הרוחב - חצי מההיקף פחות האורך. אחר כך שטח = אורך × רוחב."
                    : "אורך ורוחב יחד הם חצי מההיקף. כשיודעים את הרוחב, כופלים אותו באורך.");
        }
        // 2026-11-11: the kinds added for "מכל הסוגים" - checked first ("0.3 + 0.4" is not "3 + 0").
        if (q.matches(".*\\b0\\.\\d.*") || q.contains("מספר עשרוני")) {
            return way(q, v == 0
                    ? "0.3 זה 3 עשיריות. מחברים עשיריות כמו שמחברים מספרים רגילים, ושמים את הנקודה במקום."
                    : "כדי לכתוב שבר כמספר עשרוני, מחפשים שבר שווה עם 10 למטה - המספר שלמעלה הוא מספר העשיריות.");
        }
        if (q.startsWith("איזה מספר חסר?") && FRACTION.matcher(q).find()) {
            return way(q, v == 0
                    ? "בדקו בכמה הוכפל המספר שלמטה. את המספר שלמעלה כופלים באותו מספר."
                    : "שבר שווה נוצר כשכופלים למעלה ולמטה באותו מספר. מצאו את המספר הזה מהמספרים שלמטה.");
        }
        if (q.startsWith("צמצמו")) {
            return way(q, v == 0
                    ? "מחפשים מספר שמחלק גם את המספר שלמעלה וגם את שלמטה, ומחלקים את שניהם בו. ממשיכים עד שאין עוד מספר כזה."
                    : "נסו לחלק את שניהם ב-2, אחר כך ב-3, ב-5... עד שאי אפשר יותר.");
        }
        if (q.matches(".*(חצי|שליש|רבע|חמישית) מהן.*")) {
            return way(q, v == 0
                    ? "חצי זה לחלק ל-2, שליש ל-3, רבע ל-4. מחלקים את הכמות לחלקים שווים ולוקחים חלק אחד."
                    : "חלקו את הכמות לקבוצות שוות. אם שואלים כמה נשארו - מורידים מהכול את מה שנתנו.");
        }
        if (q.contains("היקף") && q.contains("הרוחב")) {
            return way(q, v == 0
                    ? "אורך ורוחב יחד הם חצי מההיקף. מחלקים את ההיקף ב-2, ומורידים את האורך."
                    : "ההיקף הוא אורך + רוחב + אורך + רוחב. נסו רוחב, ובדקו אם יוצא ההיקף.");
        }
        if (q.contains("שטח") && q.contains("אורך הצלע")) {
            return way(q, v == 0
                    ? "שטח ריבוע = צלע × צלע. מחפשים מספר שכפול עצמו נותן את השטח."
                    : "נסו: 5 × 5, 6 × 6, 7 × 7... איזה מהם יוצא בדיוק השטח?");
        }
        if (q.contains("קו ישר")) {
            return way(q, v == 0
                    ? "שתי זוויות צמודות על קו ישר יחד הן 180 מעלות. מחסרים מ-180 את הזווית הידועה."
                    : "קו ישר הוא חצי סיבוב - 180 מעלות. כמה חסר לזווית שנתונה כדי להגיע ל-180?");
        }
        if (q.contains("ישר-זווית") && q.contains("החדה")) {
            return way(q, v == 0
                    ? "במשולש ישר-זווית יש זווית של 90°. שתי הזוויות האחרות יחד הן 90°."
                    : "סכום הזוויות במשולש 180°. כבר יש 90° ועוד הזווית הנתונה - כמה חסר עד 180?");
        }
        if (q.startsWith("איך קוראים לצורה")) {
            return way(q, v == 0
                    ? "השם בא מהמספר: מחומש - חמש, משושה - שש, מתומן - שמונה. משולש - שלוש, מרובע - ארבע."
                    : "חפשו בשם את המספר: ח-מ-ש במחומש, ש-ש במשושה, ש-מ-נ במתומן.");
        }
        if (q.contains("צבוע")) {
            return way(q, v == 0
                    ? "סופרים לכמה חלקים שווים חולקה הצורה - זה המספר שלמטה בשבר. אחר כך סופרים את החלקים הצבועים - זה המספר שלמעלה."
                    : "שבר אומר: כמה חלקים לקחנו (למעלה) מתוך כמה חלקים יש בסך הכול (למטה). תספרו קודם את כל החלקים.");
        }
        if (q.contains("איזה שבר הכי גדול")) {
            return way(q, v == 0
                    ? "כשהמספר שלמטה זהה בכל השברים, כל החלקים באותו גודל - אז מי שיש לו יותר חלקים (מספר גדול למעלה) הוא הגדול."
                    : "כשהמספר שלמעלה זהה, תחשבו על פיצה: מחלקים לפחות אנשים - כל אחד מקבל חתיכה גדולה יותר. מספר קטן למטה = חלק גדול.");
        }
        if (q.contains("איזה שבר שווה ל")) {
            return way(q, v == 0
                    ? "שבר לא משתנה כשכופלים את המספר שלמעלה ואת המספר שלמטה באותו מספר. חפשו שבר שבו שניהם הוכפלו באותו מספר."
                    : "חצי פיצה זה כמו 2 רבעים: יותר חתיכות, אבל קטנות יותר. בדקו בכמה הוכפל המספר שלמטה, והאם גם שלמעלה הוכפל באותו מספר.");
        }
        Matcher f = FRACTION.matcher(q);
        if (f.find() && (q.contains("+") || q.contains(" - "))) {
            String d1 = f.group(2);
            String d2 = f.find() ? f.group(2) : d1;
            if (d1.equals(d2)) {
                return way(q, v == 0
                        ? "המספר שלמטה זהה (" + d1 + "), אז החלקים באותו גודל: מחברים או מחסרים רק את המספרים שלמעלה, והמספר שלמטה נשאר " + d1 + "."
                        : "תחשבו על חתיכות באותו גודל: יש לכם כמה חתיכות, ומוסיפים או מורידים חתיכות. גודל החתיכה (המספר שלמטה) לא משתנה.");
            }
            return way(q, v == 0
                    ? "המספרים שלמטה שונים (" + d1 + " ו-" + d2 + "). קודם הופכים את השבר עם " + d1 + " לשבר עם " + d2
                            + " למטה - כופלים למעלה ולמטה באותו מספר - ואז מחברים רק את המספרים שלמעלה."
                    : "אי אפשר לחבר חתיכות בגדלים שונים. מחליפים קודם לחתיכות באותו גודל (מכנה משותף), ובסוף בודקים אם אפשר לצמצם.");
        }
        if (FRACTION.matcher(q).find() && q.contains(" מ-")) {
            return way(q, v == 0
                    ? "מחלקים את המספר הגדול למספר החלקים (המספר שלמטה), ואז לוקחים כמה חלקים כאלה שכתוב למעלה."
                    : "דמיינו שמחלקים את הכמות לקבוצות שוות לפי המספר שלמטה, וסופרים כמה קבוצות לקחנו לפי המספר שלמעלה.");
        }
        if (q.contains("היקף")) {
            return way(q, v == 0
                    ? "היקף זה המרחק מסביב לצורה: מחברים את כל הצלעות. במלבן יש 2 אורכים ו-2 רוחבים, בריבוע 4 צלעות שוות."
                    : "דמיינו נמלה שהולכת על הקו מסביב לצורה, עד שחוזרת להתחלה - כמה ס\"מ היא הלכה? כל צלע נספרת.");
        }
        if (q.contains("שטח") && q.contains("משולש")) {
            return way(q, v == 0
                    ? "משולש ישר-זווית הוא בדיוק חצי ממלבן: מחשבים בסיס × גובה כמו במלבן, ואז לוקחים חצי."
                    : "חתכו מלבן באלכסון - קיבלתם שני משולשים זהים. אז שטח המשולש הוא חצי משטח המלבן.");
        }
        if (q.contains("שטח")) {
            return way(q, v == 0
                    ? "שטח זה כמה ריבועים קטנים של 1 ס\"מ נכנסים בתוך הצורה. במלבן: אורך × רוחב. בריבוע: צלע × צלע."
                    : "דמיינו שורות של אריחים: בכל שורה יש מספר אריחים כמו האורך, ויש שורות כמו הרוחב. כמה אריחים בסך הכול?");
        }
        if (q.contains("זווית")) {
            return way(q, v == 0
                    ? "סכום שלוש הזוויות בכל משולש הוא תמיד 180 מעלות. מחסרים מ-180 את שתי הזוויות שכבר ידועות."
                    : "קודם מחברים את שתי הזוויות שנתונות, ואז בודקים כמה חסר כדי להגיע ל-180.");
        }
        if (q.contains("צלעות")) {
            return way(q, v == 0
                    ? "צלע היא קו ישר אחד בשפת הצורה. שימו אצבע על פינה אחת וספרו קווים עד שחוזרים אליה."
                    : "אפשר לספור פינות במקום קווים - בצורה סגורה יש בדיוק אותו מספר של פינות וצלעות.");
        }
        return null;
    }

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
        // 2026-11-11 (test): 265 - 135 -> "קודם מורידים 130" - and 130 IS the answer. Never show it.
        boolean revealsAnswer = tens == a - b || ones == a - b;
        return tens > 0 && ones > 0 && !revealsAnswer
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

    /** "מה המספר החסר? 7 + ? = 12" - think of the opposite operation, never say the number. */
    private static Way missingNumber(String q, int v) {
        String op = q.contains("+") ? "+" : q.contains("×") ? "×" : q.contains("÷") ? "÷" : "-";
        boolean first = q.matches(".*\\?\\s*[+\\-×÷].*");
        String opposite = switch (op) {
            case "+" -> "חיסור: מהתוצאה מורידים את המספר שכבר ידוע";
            case "-" -> first ? "חיבור: מחברים את התוצאה ואת המספר שהורידו" : "חיסור: מהמספר הגדול מורידים את התוצאה";
            case "×" -> "חילוק: מחלקים את התוצאה במספר שכבר ידוע";
            default -> first ? "כפל: כופלים את התוצאה במספר שמחלקים בו" : "חילוק: מחלקים את המספר הגדול בתוצאה";
        };
        return way(q, v == 0
                ? "את המספר החסר מוצאים עם הפעולה ההפוכה - " + opposite + "."
                : "נסו לשים מספר במקום סימן השאלה ולבדוק אם יוצאת התוצאה. אם יצא יותר מדי - נסו מספר קטן יותר.");
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
