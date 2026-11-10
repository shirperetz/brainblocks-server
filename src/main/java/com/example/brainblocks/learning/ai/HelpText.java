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

    /** May this text be shown to a child for a question whose answer is {@code answer}? */
    public static boolean safe(String text, String answer) {
        if (text == null || text.isBlank()) return false;
        if (LATIN_OR_LINK.matcher(text).find()) return false;
        if (answer != null && !answer.isBlank()
                && Pattern.compile("(?<!\\d)" + Pattern.quote(answer.trim()) + "(?!\\d)").matcher(text).find()) {
            return false; // gives the answer away
        }
        return true;
    }
}
