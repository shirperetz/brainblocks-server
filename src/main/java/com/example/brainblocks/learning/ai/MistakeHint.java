package com.example.brainblocks.learning.ai;

import com.example.brainblocks.learning.service.Mistakes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-11-13 (Shir chose: explanations without spending tokens). Two free helpers for "לא הבנתי":
 * a sentence aimed at the child's own mistake (from Mistakes - which kind of wrong answer it was), and
 * a small drawing for the question (number line, tens and ones, equal groups, sharing, a fraction).
 * Neither ever says the answer: the drawing shows the question's numbers, the child does the last step.
 */
public final class MistakeHint {

    private MistakeHint() {}

    /** For the child, by mistake kind - a nudge, never the answer. */
    private static final Map<String, String> FOR_CHILD = new LinkedHashMap<>();
    static {
        FOR_CHILD.put(Mistakes.CARRY, "נראה ששכחת את העשרת: כשהאחדות עוברות 10, מעבירים 1 לעשרות (או לוקחים 1 מהעשרות).");
        FOR_CHILD.put(Mistakes.PLACE_VALUE, "שימו לב לאחדות ולעשרות: מפרקים כל מספר לעשרות ואחדות ומחשבים כל חלק לבד.");
        FOR_CHILD.put(Mistakes.TIMES_TABLE, "התשובה שבחרת שייכת לשורה קרובה בלוח הכפל. בדקו שוב כמה קבוצות יש וכמה בכל קבוצה.");
        FOR_CHILD.put(Mistakes.COUNTING, "כמעט! התשובה שבחרת קרובה מאוד. ספרו שוב לאט, אחד-אחד.");
        FOR_CHILD.put(Mistakes.WRONG_OPERATION, "בדקו את הסימן בתרגיל: מוסיפים, מורידים, או קבוצות שוות?");
        FOR_CHILD.put(Mistakes.SEQUENCE_STEP, "בדקו כמה מוסיפים (או מורידים) בין כל שני מספרים בסדרה - הקפיצה תמיד אותה קפיצה.");
        FOR_CHILD.put(Mistakes.REVERSED, "שימו לב מה למעלה ומה למטה: למעלה - כמה חלקים לקחנו, למטה - לכמה חלקים חילקנו.");
        FOR_CHILD.put(Mistakes.FRACTION_MEANING, "המספר למטה אומר לכמה חלקים שווים חילקנו, והמספר למעלה - כמה חלקים לקחנו.");
        FOR_CHILD.put(Mistakes.ADD_DENOMINATORS, "כשהמספרים למטה שווים, החלקים באותו גודל: מחברים רק את המספרים למעלה, והמספר למטה נשאר.");
        FOR_CHILD.put(Mistakes.NO_COMMON_DENOMINATOR, "קודם מביאים את השברים לאותו מספר למטה, ורק אחר כך מחברים.");
        FOR_CHILD.put(Mistakes.PART_WHOLE, "קראו שוב את השאלה: שואלים כמה נשאר, כמה נתנו, או כמה יש בסך הכל?");
        FOR_CHILD.put(Mistakes.INCOMPLETE, "עצרת באמצע הדרך! יש עוד שלב אחד אחרי מה שחישבת.");
        FOR_CHILD.put(Mistakes.DECIMAL_PLACE, "שימו לב לנקודה: 0.3 זה שלוש עשיריות. חשבו בעשיריות, ושימו את הנקודה בסוף.");
        FOR_CHILD.put(Mistakes.AREA_PERIMETER, "היקף זה הגדר מסביב (מחברים את הצלעות), שטח זה כמה ריבועים בפנים (כופלים).");
        FOR_CHILD.put(Mistakes.WRONG_MEASURE, "חפשו את הגובה: הקו הישר מהבסיס למעלה, לא הצלע המשופעת.");
        FOR_CHILD.put(Mistakes.ANGLE_SUM, "זכרו: קו ישר ומשולש - 180°, סיבוב שלם ומרובע - 360°, פינה ישרה - 90°.");
        FOR_CHILD.put(Mistakes.ANGLE_TYPE, "השוו לפינה של דף: קטנה ממנה - חדה, בדיוק כמוה - ישרה, גדולה ממנה - קהה.");
        FOR_CHILD.put(Mistakes.SHAPE_NAMES, "השם בא מהמספר: משולש - 3, מרובע - 4, מחומש - 5, משושה - 6, מתומן - 8.");
        // 2026-11-21: the new topics
        FOR_CHILD.put(Mistakes.PARITY, "הסתכלו רק על הספרה האחרונה: 0, 2, 4, 6, 8 - זוגי. 1, 3, 5, 7, 9 - אי-זוגי.");
        FOR_CHILD.put(Mistakes.ORDER_OF_OPS, "לא מחשבים משמאל לימין! קודם סוגריים, אחר כך כפל וחילוק, ורק בסוף חיבור וחיסור.");
        FOR_CHILD.put(Mistakes.ROUNDING_RULE, "מסתכלים רק על הספרה שאחרי המקום שמעגלים אליו: 0 עד 4 - למטה, 5 עד 9 - למעלה.");
        FOR_CHILD.put(Mistakes.DIVISIBILITY_RULE, "בדקו את הסימן: ב-2 - ספרה אחרונה זוגית, ב-5 - נגמר ב-0 או 5, ב-3 - סכום הספרות מתחלק ב-3.");
        FOR_CHILD.put(Mistakes.UNIT_CONVERSION, "כמה יש ביחידה? מטר = 100 ס״מ, קילוגרם = 1,000 גרם, שעה = 60 דקות.");
        FOR_CHILD.put(Mistakes.PERCENT_MEANING, "אחוז זה חלק מ-100: 50% זה חצי, 25% זה רבע, 10% זה עשירית.");
        FOR_CHILD.put(Mistakes.RATIO_SCALE, "ביחס שואלים 'פי כמה?' - כמה פעמים גדל המספר הראשון, ובאותו מספר כופלים גם את השני.");
        FOR_CHILD.put(Mistakes.DATA_READING, "שימו את האצבע על העמודה, ולכו איתה בקו ישר אל המספר.");
        FOR_CHILD.put(Mistakes.CIRCLE_FORMULA, "קוטר זה פעמיים רדיוס. היקף = קוטר × 3.14, ושטח = רדיוס × רדיוס × 3.14.");
        FOR_CHILD.put(Mistakes.VOLUME_FORMULA, "נפח = אורך × רוחב × גובה - כמה קוביות נכנסות בפנים.");
        FOR_CHILD.put(Mistakes.INVERSE_OP, "עושים את הפעולה ההפוכה: אם הוסיפו ל-x - מחסירים, אם כפלו את x - מחלקים.");
    }

    /** A sentence for this child's wrong answer, or null (right / no answer / a mistake we don't know). */
    public static String forMistake(String question, String correct, String chosen) {
        String type = Mistakes.classify(null, question, correct, chosen);
        return type == null ? null : FOR_CHILD.get(type);
    }

    private static final Pattern ARITH = Pattern.compile("(\\d+)\\s*([+\\-×÷:])\\s*(\\d+)");
    private static final Pattern FRACTION = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])");

    /**
     * A drawing for the question, or null. Kinds: "line" (start, jump - small additions), "blocks"
     * (numbers as tens and ones), "groups" (groups x each), "share" (total into groups), "fraction"
     * (parts of whole). Only the question's numbers - never the result.
     */
    public static Map<String, Object> visual(String question) {
        if (question == null) return null;
        Matcher m = ARITH.matcher(question);
        if (m.find() && !question.contains("?" + " =") && !question.contains("החסר")) {
            int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(3));
            String op = m.group(2);
            switch (op) {
                case "+" -> {
                    if (a + b <= 20 && b <= 10) return Map.of("kind", "line", "start", a, "jump", b, "sign", "+");
                    if (a < 1000 && b < 1000) return Map.of("kind", "blocks", "numbers", List.of(a, b), "sign", "+");
                }
                case "-" -> {
                    if (a <= 20 && b <= a) return Map.of("kind", "line", "start", a, "jump", b, "sign", "-");
                    if (a < 1000 && b < 1000) return Map.of("kind", "blocks", "numbers", List.of(a, b), "sign", "-");
                }
                case "×" -> {
                    if (a <= 10 && b <= 10 && a > 0 && b > 0) return Map.of("kind", "groups", "groups", a, "each", b);
                }
                case "÷", ":" -> {
                    if (a <= 60 && b > 0 && b <= 10) return Map.of("kind", "share", "total", a, "groups", b);
                }
                default -> { }
            }
            return null;
        }
        Matcher f = FRACTION.matcher(question);
        if (f.find()) {
            int n = Integer.parseInt(f.group(1)), d = Integer.parseInt(f.group(2));
            if (d >= 2 && d <= 12 && n <= d) return Map.of("kind", "fraction", "parts", n, "whole", d);
        }
        return null;
    }
}
