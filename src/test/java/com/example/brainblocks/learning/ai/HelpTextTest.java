package com.example.brainblocks.learning.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnerProfile;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HelpTextTest {

    @Test
    void neverShowsTheAnswer() {
        // A real reply from the model (2026-11-10) that solved the series to the end.
        String leak = "כל מספר גדל ב-4. אז כדי למצוא את המספר הבא, נוסיף 4 ל-15: 15 + 4 = 19.";
        assertFalse(HelpText.safe(leak, "19"));
        assertTrue(HelpText.safe("כל מספר גדל ב-4. מה יוצא אם מוסיפים 4 למספר האחרון?", "19"));
        // "8" inside "48" or "18" is not the answer 8.
        assertTrue(HelpText.safe("חשבו על 48 חלקי 6, ונסו לספור בקפיצות של 6 עד 18 ואז הלאה.", "8"));
        assertFalse(HelpText.safe("התשובה היא 8.", "8"));
    }

    @Test
    void onlyHebrewNoLinks() {
        assertFalse(HelpText.safe("נסו את https://example.com", "5"));
        assertFalse(HelpText.safe("Think of groups.", "5"));
        assertFalse(HelpText.safe("", "5"));
        assertTrue(HelpText.safe("דמיינו 7 קופסאות, ובכל אחת 8 צעצועים.", "56"));
    }

    @Test
    void shortAndPlain() {
        String raw = "**כדי לענות:**\n1. דמיינו קבוצות.\n2. ספרו את כולן.\nאחר כך בדקו שוב. ועוד משפט רביעי שלא צריך.";
        String c = HelpText.clean(raw);
        assertEquals("כדי לענות: דמיינו קבוצות. ספרו את כולן. אחר כך בדקו שוב.", c);
        assertTrue(HelpText.clean("א".repeat(1000)).length() <= HelpText.MAX_CHARS);
    }

    @Test
    void modelOffFallsBackToTheGamesHint() {
        HelpService off = new HelpService(false, "http://localhost:1", "x");
        HelpService.Help h = off.explain("כמה זה 3 + 4?", "7", null, "G1", false, "תתחילו מ-3 ותספרו עוד 4");
        assertEquals("hint", h.source());
        assertEquals("תתחילו מ-3 ותספרו עוד 4", h.text());
        HelpService unreachable = new HelpService(true, "http://localhost:1", "x");
        assertEquals("hint", unreachable.explain("כמה זה 3 + 4?", "7", null, "G1", false, "רמז").source());
    }

    @Test
    void dailyStreak() {
        LearnerProfile p = new LearnerProfile("בדיקה", Avatar.values()[0]);
        LocalDate d = LocalDate.of(2026, 11, 10);
        assertTrue(p.completeDaily(d));
        assertEquals(1, p.getDailyStreak());
        assertFalse(p.completeDaily(d)); // twice the same day counts once
        assertTrue(p.completeDaily(d.plusDays(1)));
        assertEquals(2, p.getDailyStreak());
        assertTrue(p.completeDaily(d.plusDays(4))); // a gap starts again
        assertEquals(1, p.getDailyStreak());
    }
}
