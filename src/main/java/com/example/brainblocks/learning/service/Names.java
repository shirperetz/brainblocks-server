package com.example.brainblocks.learning.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-21 (security pass): names children and teachers type. Hidden characters are removed -
 * control characters, zero-width spaces and direction overrides (U+202E could flip the text on the
 * teacher's screen) - spaces/line breaks become one space, and a name must keep something visible.
 */
public final class Names {
    private Names() {}

    public static String clean(Object raw, int max, String what) {
        String s = raw == null ? "" : raw.toString();
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (type == Character.CONTROL || type == Character.FORMAT) {
                if (Character.isWhitespace(cp)) b.append(' ');
                return;
            }
            b.appendCodePoint(Character.isWhitespace(cp) || Character.isSpaceChar(cp) ? ' ' : cp);
        });
        String out = b.toString().replaceAll(" +", " ").strip();
        if (out.codePoints().noneMatch(cp -> Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.OTHER_SYMBOL)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, what + " ריק - כתבו לפחות אות אחת");
        }
        if (out.codePointCount(0, out.length()) > max) out = out.substring(0, out.offsetByCodePoints(0, max)).strip();
        return out;
    }
}
