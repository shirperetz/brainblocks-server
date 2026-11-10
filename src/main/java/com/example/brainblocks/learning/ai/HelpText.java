package com.example.brainblocks.learning.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safety + shape rules for what the AI helper may show a child (pure, unit-tested):
 * short (at most 3 sentences / 320 chars), Hebrew only (no Latin letters, no links), and never
 * the final answer to the question itself - the child should still get there alone.
 */
public final class HelpText {

    static final int MAX_CHARS = 320;
    static final int MAX_SENTENCES = 3;
    private static final Pattern LATIN_OR_LINK = Pattern.compile("[A-Za-z]|www\\.|https?:");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?](?=\\s|$)");
    /** Hebrew letters only (a reply once ended a word in an Arabic letter), digits, math, punctuation. */
    private static final Pattern ALLOWED = Pattern.compile("[\\u05D0-\\u05EA\\u05F3\\u05F4\\d\\s.,!?:;'\"()\\-–—+×÷=*/%…]*");
    /** "a op b = c" written by the model - checked, since an 8B model sometimes gets them wrong. */
    private static final Pattern EQUATION = Pattern.compile("(\\d+)\\s*([+\\-×*x÷/:])\\s*(\\d+)\\s*=\\s*(\\d+)");

    private HelpText() {}

    /** Tidies the model's reply: no markdown, no numbered lists, first 3 sentences. null if empty. */
    public static String clean(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("[*#_`>]", "")
                .replaceAll("(?m)^\\s*(\\d+[.)]|[-•])\\s+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (t.isEmpty()) return null;
        StringBuilder out = new StringBuilder();
        Matcher m = SENTENCE_END.matcher(t);
        int start = 0;
        int sentences = 0;
        while (sentences < MAX_SENTENCES && m.find()) {
            String s = t.substring(start, m.end()).trim();
            if (out.length() + s.length() + 1 > MAX_CHARS && sentences > 0) break;
            if (out.length() > 0) out.append(' ');
            out.append(s);
            start = m.end();
            sentences++;
        }
        if (sentences == 0) out.append(t.length() > MAX_CHARS ? t.substring(0, MAX_CHARS) : t);
        return out.toString().trim();
    }

    /** Numbers written as words ("אחת-עשרה") can't be checked - a reply once counted 18 + 1 that way, wrongly. */
    private static final Pattern NUMBER_WORDS = Pattern.compile(
            // Place names ("עשרות", "מאות") are fine - the code's own explanations use them.
            "עשרה|עשרים|שלושים|ארבעים|חמישים|שישים|שבעים|שמונים|תשעים|(?<![\u05D0-\u05EA])(?:מאה|מאתיים|אלף|אלפיים)(?![\u05D0-\u05EA])");

    /** The rephrasing kept every number of the correct way (so it still describes the same steps). */
    public static boolean keepsNumbers(String reply, String correctWay) {
        return Tutor.numbersIn(reply).containsAll(Tutor.numbersIn(correctWay));
    }

    /** Every number in the text is one we know is right here (from the question / the code's explanation). */
    public static boolean onlyNumbers(String text, java.util.Set<Long> allowed) {
        for (Long n : Tutor.numbersIn(text)) {
            if (!allowed.contains(n)) return false;
        }
        return true;
    }

    /** True when the text contains a worked equation that is false ("300 + 100 = 470"). */
    static boolean hasWrongEquation(String text) {
        Matcher m = EQUATION.matcher(text);
        while (m.find()) {
            long a = Long.parseLong(m.group(1));
            long b = Long.parseLong(m.group(3));
            long c = Long.parseLong(m.group(4));
            long r = switch (m.group(2)) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "÷", "/", ":" -> b == 0 || a % b != 0 ? Long.MIN_VALUE : a / b;
                default -> a * b;
            };
            if (r != c) return true;
        }
        return false;
    }

    /** May this text be shown to a child for a question whose answer is {@code answer}? */
    public static boolean safe(String text, String answer) {
        if (text == null || text.isBlank()) return false;
        if (LATIN_OR_LINK.matcher(text).find()) return false;
        if (!ALLOWED.matcher(text).matches()) return false;
        if (hasWrongEquation(text)) return false;
        if (NUMBER_WORDS.matcher(text).find()) return false;
        if (answer != null && !answer.isBlank()
                && Pattern.compile("(?<!\\d)" + Pattern.quote(answer.trim()) + "(?!\\d)").matcher(text).find()) {
            return false; // gives the answer away
        }
        return true;
    }
}
