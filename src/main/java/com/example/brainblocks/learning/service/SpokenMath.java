package com.example.brainblocks.learning.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 2026-11-12 (🔊 "read every question aloud"): the local voice (BlueTTS) skips digits - "כמה זה 38 + 27?"
 * would be read "כמה זה ועוד". Every text read aloud is turned into words first, the way a teacher
 * says it: numbers in the counting (feminine) form ("שלושים ושמונה"), + - × ÷ : = as words, fractions
 * as fractions ("שלוש חמישיות"), 90° as "תשעים מעלות", 0.5 as "אפס נקודה חמש".
 */
public final class SpokenMath {

    private SpokenMath() {}

    private static final String[] ONES = {"אפס", "אחת", "שתיים", "שלוש", "ארבע", "חמש", "שש", "שבע", "שמונה", "תשע"};
    private static final String[] TEENS = {"עשר", "אחת עשרה", "שתים עשרה", "שלוש עשרה", "ארבע עשרה", "חמש עשרה",
            "שש עשרה", "שבע עשרה", "שמונה עשרה", "תשע עשרה"};
    private static final String[] TENS = {"", "", "עשרים", "שלושים", "ארבעים", "חמישים", "שישים", "שבעים", "שמונים", "תשעים"};
    private static final String[] HUNDREDS = {"", "מאה", "מאתיים", "שלוש מאות", "ארבע מאות", "חמש מאות", "שש מאות",
            "שבע מאות", "שמונה מאות", "תשע מאות"};
    private static final String[] THOUSANDS = {"", "אלף", "אלפיים", "שלושת אלפים", "ארבעת אלפים", "חמשת אלפים",
            "ששת אלפים", "שבעת אלפים", "שמונת אלפים", "תשעת אלפים", "עשרת אלפים"};

    /** 0..999999 in words; bigger numbers stay digits (the games never go there). */
    public static String number(long n) {
        if (n < 0) return "מינוס " + number(-n);
        if (n >= 1_000_000) return Long.toString(n);
        if (n < 10) return ONES[(int) n];
        java.util.List<String> parts = new java.util.ArrayList<>();
        long thousands = n / 1000;
        int rest = (int) (n % 1000);
        if (thousands > 0) parts.add(thousands <= 10 ? THOUSANDS[(int) thousands] : below1000((int) thousands) + " אלף");
        if (rest > 0) {
            int h = rest / 100;
            int below100 = rest % 100;
            if (h > 0) parts.add(HUNDREDS[h]);
            if (below100 > 0) parts.addAll(below100Parts(below100));
        }
        // "ו" before the last part: "מאה עשרים ושלוש", "אלף וחמישים", "מאה וחמש"
        if (parts.size() > 1) {
            int last = parts.size() - 1;
            parts.set(last, "ו" + parts.get(last));
        }
        return String.join(" ", parts);
    }

    private static String below1000(int n) {
        return number(n);
    }

    private static java.util.List<String> below100Parts(int n) {
        if (n < 10) return java.util.List.of(ONES[n]);
        if (n < 20) return java.util.List.of(TEENS[n - 10]);
        if (n % 10 == 0) return java.util.List.of(TENS[n / 10]);
        return java.util.List.of(TENS[n / 10], ONES[n % 10]); // "עשרים", "ושלוש" (the ו is added above)
    }

    private static final Pattern FRACTION = Pattern.compile("(?<![\\d.])(\\d+)/(\\d+)(?![\\d.])");
    private static final Pattern DECIMAL = Pattern.compile("(\\d+)\\.(\\d+)");
    private static final Pattern DEGREES = Pattern.compile("(\\d+)\\s*°");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** A question or explanation, ready for the voice. */
    public static String toSpeech(String text) {
        if (text == null) return "";
        String t = text;
        t = replace(FRACTION, t, m -> fraction(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        t = replace(DECIMAL, t, m -> number(Long.parseLong(m.group(1))) + " נקודה " + digits(m.group(2)));
        t = replace(DEGREES, t, m -> number(Long.parseLong(m.group(1))) + (m.group(1).equals("1") ? " מעלה" : " מעלות"));
        // 2026-11-21 (the new topics): x, %, brackets around a formula, π, units written short
        t = t.replaceAll("(\\d)%", "$1 אחוז").replaceAll("(?<![A-Za-z])x(?![A-Za-z])", " איקס")
                .replaceAll("\\(\\s*(?=[\\d איקס])(?=\\s*(\\d|איקס))", " פותחים סוגריים ")
                .replaceAll("(?<=\\d)\\s*\\)", " סוגרים סוגריים ")
                .replace("π", " פאי ").replace("≈", " בערך ")
                .replace("סמ״ק", "סנטימטר מעוקב").replace("סמ״ר", "סנטימטר רבוע").replace("קמ״ש", "קילומטר לשעה")
                .replace("ס״מ", "סנטימטר").replace("ק״מ", "קילומטר").replace("ק״ג", "קילוגרם").replace("ש״ח", "שקלים")
                .replace("מ״ל", "מיליליטר");
        t = t.replace("×", " כפול ").replace("÷", " חלקי ").replace("=", " שווה ")
                .replace(" + ", " ועוד ").replace(" - ", " פחות ").replace(" − ", " פחות ")
                .replace(" : ", " חלקי ").replace("?", "? ").replace("_", " ");
        // A lone "?" standing for the missing number ("7 + ? = 12", "11, ?") is said "כמה".
        t = t.replaceAll("(^|[\\s,])\\?(?=\\s|$)", "$1 כמה ");
        t = replace(NUMBER, t, m -> number(Long.parseLong(m.group())));
        return t.replaceAll("\\s+", " ").replace(" ?", "?").trim();
    }

    /** "3/5" -> "שלוש חמישיות"; outside the known words: "שבע עשרה חלקי חמש". */
    static String fraction(int n, int d) {
        if (n >= 1 && n < 10 && d >= 2 && d <= 10 && n < d) return FractionGeometryQuestions.fractionWords(n, d);
        return number(n) + " חלקי " + number(d);
    }

    private static String digits(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (b.length() > 0) b.append(' ');
            b.append(ONES[c - '0']);
        }
        return b.toString();
    }

    private static String replace(Pattern p, String text, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(f.apply(m)));
        m.appendTail(out);
        return out.toString();
    }
}
