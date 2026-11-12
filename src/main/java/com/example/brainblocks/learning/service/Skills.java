package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.Topic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-11-11 (Shir: "I want to see what she finds hard and what she doesn't"): every answer is saved
 * with the SKILL it practiced, not only its topic - "fractions 62%" becomes "simplifying 9/10, adding
 * with different denominators 3/8". Fractions and geometry are tagged where the question is built
 * (the kind is known there); arithmetic, comparison, sequences, word problems and the island games
 * are recognised here from the question's text, which those generators write in fixed forms.
 */
public final class Skills {

    private Skills() {}

    /** code -> Hebrew name shown to the admin/teacher. Order = the order they are listed in. */
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        // חיבור / חיסור
        NAMES.put("ADD_TO_10", "חיבור עד 10");
        NAMES.put("ADD_NO_CARRY", "חיבור בלי המרה");
        NAMES.put("ADD_CARRY", "חיבור עם המרה (נשיאה)");
        NAMES.put("SUB_TO_10", "חיסור עד 10");
        NAMES.put("SUB_NO_BORROW", "חיסור בלי פריטה");
        NAMES.put("SUB_BORROW", "חיסור עם פריטה");
        NAMES.put("MISSING_ADD", "המספר החסר בחיבור");
        NAMES.put("MISSING_SUB", "המספר החסר בחיסור");
        // כפל / חילוק
        NAMES.put("MUL_SMALL", "כפל עד 5×5");
        NAMES.put("MUL_TABLE", "לוח הכפל");
        NAMES.put("MUL_BIG", "כפל מעבר ללוח");
        NAMES.put("DIV_TABLE", "חילוק בלוח הכפל");
        NAMES.put("DIV_BIG", "חילוק מעבר ללוח");
        NAMES.put("MISSING_MUL", "המספר החסר בכפל");
        NAMES.put("MISSING_DIV", "המספר החסר בחילוק");
        // השוואה, סדרות, בעיות
        NAMES.put("CMP_BIGGEST", "מי הגדול ביותר");
        NAMES.put("CMP_SMALLEST", "מי הקטן ביותר");
        NAMES.put("SEQ_UP", "סדרה עולה");
        NAMES.put("SEQ_DOWN", "סדרה יורדת");
        NAMES.put("SEQ_BIG_STEP", "סדרה בקפיצות גדולות");
        NAMES.put("WP_ADD", "בעיה מילולית - חיבור");
        NAMES.put("WP_SUB", "בעיה מילולית - חיסור");
        NAMES.put("WP_MUL", "בעיה מילולית - כפל");
        // שברים
        NAMES.put("FRAC_WRITE", "כתיבת שבר");
        NAMES.put("FRAC_SHADED", "איזה חלק צבוע");
        NAMES.put("FRAC_AS_DIVISION", "שבר כמנת חילוק");
        NAMES.put("FRAC_VS_ONE", "קטן/שווה/גדול מ-1");
        NAMES.put("FRAC_WHOLES", "שלמים בשבר");
        NAMES.put("FRAC_WHOLES_REMAINDER", "שלמים ושארית");
        NAMES.put("FRAC_EQUIVALENT", "שברים שווים");
        NAMES.put("FRAC_MISSING_EQUIVALENT", "השלמת שבר שווה");
        NAMES.put("FRAC_SAME_DENOMINATOR", "חיבור/חיסור באותו מכנה");
        NAMES.put("FRAC_PART_OF", "שבר מכמות");
        NAMES.put("FRAC_STORY", "בעיה מילולית בשברים");
        NAMES.put("FRAC_SIMPLIFY", "צמצום");
        NAMES.put("FRAC_DECIMALS", "שברים עשרוניים");
        NAMES.put("FRAC_DIFFERENT_DENOMINATORS", "חיבור עם מכנים שונים");
        NAMES.put("FRAC_BIGGEST", "השוואת שברים");
        // זוויות
        NAMES.put("ANG_TYPE", "סוג זווית");
        NAMES.put("ANG_NAMED_DEGREES", "ישרה/שטוחה/סיבוב במעלות");
        NAMES.put("ANG_WHICH", "איזו זווית חדה/קהה");
        NAMES.put("ANG_TRIANGLE_TYPE", "סוג משולש");
        NAMES.put("ANG_STRAIGHT_LINE", "זווית על קו ישר");
        NAMES.put("ANG_TRIANGLE", "זווית חסרה במשולש");
        NAMES.put("ANG_RIGHT_TRIANGLE", "משולש ישר-זווית");
        NAMES.put("ANG_AROUND_POINT", "זוויות סביב נקודה");
        NAMES.put("ANG_ISOSCELES", "משולש שווה-שוקיים");
        NAMES.put("ANG_QUADRILATERAL", "זוויות במרובע"); // 2026-11-12: ו׳
        NAMES.put("TEACHER_OWN", "תרגילים של המורה"); // 2026-11-12: the game room
        NAMES.put("ANG_VERTICAL_ADJACENT", "זוויות קודקודיות וצמודות");
        NAMES.put("AREA_PARALLELOGRAM", "שטח מקבילית");
        NAMES.put("PER_CIRCLE", "היקף עיגול");
        // צלעות, היקף, שטח
        NAMES.put("GEO_SIDES", "כמה צלעות");
        NAMES.put("GEO_SHAPE_NAME", "שמות צורות");
        NAMES.put("PER_RECTANGLE", "היקף מלבן");
        NAMES.put("PER_TRIANGLE", "היקף משולש");
        NAMES.put("PER_SQUARE", "היקף ריבוע");
        NAMES.put("PER_MISSING_SIDE", "צלע חסרה לפי היקף");
        NAMES.put("AREA_RECTANGLE", "שטח מלבן");
        NAMES.put("AREA_SQUARE", "שטח ריבוע");
        NAMES.put("AREA_SIDE_FROM_AREA", "צלע לפי שטח");
        NAMES.put("AREA_RIGHT_TRIANGLE", "שטח משולש ישר-זווית");
        NAMES.put("AREA_FROM_PERIMETER", "שטח לפי היקף");
        NAMES.put("GEO_SQUARE", "ריבוע - היקף ושטח");
        // אי המספרים (א׳-ב׳)
        NAMES.put("ISL_COUNT_10", "ספירה עד 10");
        NAMES.put("ISL_COUNT_20", "ספירה עד 20");
        NAMES.put("ISL_COUNT_ON", "ממשיכים לספור מנקודה");
        NAMES.put("ISL_ADD_5", "חיבור עד 5 (בלונים)");
        NAMES.put("ISL_ADD_10", "חיבור עד 10 (בלונים)");
        NAMES.put("ISL_SUB_5", "חיסור עד 5 (בלונים)");
        NAMES.put("ISL_SUB_10", "חיסור עד 10 (בלונים)");
        NAMES.put("ISL_ADD_SUB_10", "חיבור וחיסור עד 10 (בלונים)");
        NAMES.put("ISL_CROSS_10", "חיבור מעבר לעשר (בלונים)");
        NAMES.put("ISL_MAKE_10", "משלימים לעשר");
        NAMES.put("ISL_MAKE_20", "משלימים לעשרים");
        NAMES.put("ISL_ORDER", "סדר המספרים");
        NAMES.put("ISL_CLOCK_HOUR", "שעון - שעות שלמות");
        NAMES.put("ISL_CLOCK_HALF", "שעון - חצאים ורבעים");
        NAMES.put("ISL_CLOCK_MIN", "שעון - דקות");
        NAMES.put("ISL_PAY", "לשלם בדיוק");
        NAMES.put("ISL_COUNT_MONEY", "לספור כסף");
        NAMES.put("ISL_CHANGE", "עודף");
        NAMES.put("OTHER", "אחר");
    }

    public static String name(String code) {
        return NAMES.getOrDefault(code, NAMES.get("OTHER"));
    }

    public static boolean known(String code) {
        return code != null && NAMES.containsKey(code);
    }

    private static final Pattern PLAIN = Pattern.compile("כמה זה (\\d+) ([+\\-×÷]) (\\d+)\\?");
    private static final Pattern MISSING = Pattern.compile("מה המספר החסר\\? .*([+\\-×÷]) .*= \\d+");
    private static final Pattern SEQUENCE = Pattern.compile("מה המספר הבא\\? (\\d+), (\\d+),");

    /**
     * The skill of one answer: the tag the client sent back when it is a known one, otherwise
     * recognised from the question's topic, stage and text. Never null - unknown is "OTHER".
     */
    public static String of(String sentSkill, Topic topic, int stage, String text) {
        if (known(sentSkill) && !"OTHER".equals(sentSkill)) return sentSkill;
        String t = text == null ? "" : text;
        if (topic == null) return "OTHER";
        switch (topic) {
            case DRAGON:
                return stage == 4 || stage == 6 ? "ISL_COUNT_ON" : stage == 5 ? "ISL_COUNT_20" : "ISL_COUNT_10";
            case BALLOONS:
                return switch (stage) {
                    case 1 -> "ISL_ADD_5";
                    case 2 -> "ISL_ADD_10";
                    case 3 -> "ISL_SUB_5";
                    case 4 -> "ISL_SUB_10";
                    case 5 -> "ISL_ADD_SUB_10";
                    default -> "ISL_CROSS_10";
                };
            case BRIDGE:
                return stage >= 5 ? "ISL_MAKE_20" : "ISL_MAKE_10";
            case TRAIN:
                return "ISL_ORDER";
            case CLOCK:
                return stage <= 2 ? "ISL_CLOCK_HOUR" : stage <= 5 ? "ISL_CLOCK_HALF" : "ISL_CLOCK_MIN";
            case SHOP:
                return "ISL_PAY";
            case COINS:
                return stage == 6 ? "ISL_CHANGE" : "ISL_COUNT_MONEY";
            default:
                break;
        }
        Matcher m = MISSING.matcher(t);
        if (m.find()) {
            return switch (m.group(1)) {
                case "+" -> "MISSING_ADD";
                case "-" -> "MISSING_SUB";
                case "×" -> "MISSING_MUL";
                default -> "MISSING_DIV";
            };
        }
        m = PLAIN.matcher(t);
        if (m.find()) {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(3));
            return arithmetic(m.group(2), a, b);
        }
        if (t.startsWith("איזה המספר הגדול")) return "CMP_BIGGEST";
        if (t.startsWith("איזה המספר הקטן")) return "CMP_SMALLEST";
        m = SEQUENCE.matcher(t);
        if (m.find()) {
            int step = Integer.parseInt(m.group(2)) - Integer.parseInt(m.group(1));
            if (step < 0) return "SEQ_DOWN";
            return step > 10 ? "SEQ_BIG_STEP" : "SEQ_UP";
        }
        if (topic == Topic.WORD_PROBLEMS) {
            if (t.contains("הלכו לאיבוד")) return "WP_SUB";
            if (t.contains("שקיות")) return "WP_MUL";
            return "WP_ADD";
        }
        return "OTHER";
    }

    /** "a op b" by what makes it hard: a carry / a borrow / past the times table. */
    static String arithmetic(String op, int a, int b) {
        return switch (op) {
            case "+" -> a + b <= 10 ? "ADD_TO_10" : (a % 10) + (b % 10) >= 10 ? "ADD_CARRY" : "ADD_NO_CARRY";
            case "-" -> a <= 10 ? "SUB_TO_10" : (a % 10) < (b % 10) ? "SUB_BORROW" : "SUB_NO_BORROW";
            case "×" -> a <= 5 && b <= 5 ? "MUL_SMALL" : a <= 10 && b <= 10 ? "MUL_TABLE" : "MUL_BIG";
            default -> b <= 10 && a / Math.max(b, 1) <= 10 ? "DIV_TABLE" : "DIV_BIG";
        };
    }
}
