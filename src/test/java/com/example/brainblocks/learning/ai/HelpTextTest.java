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
    void wrongMathAndOtherAlphabetsAreBlocked() {
        // Real replies (2026-11-10): a wrong sum, and a Hebrew word ending in an Arabic letter.
        assertFalse(HelpText.safe("פרקו לפי מקומות: 300 + 100 = 470, ואז המשיכו.", "445"));
        assertTrue(HelpText.safe("פרקו לפי מקומות: 300 + 100 = 400, ואז המשיכו.", "445"));
        assertFalse(HelpText.safe("כמו שעשיתم עם מספרים קטנים.", "445"));
        assertTrue(HelpText.safe("כמו שעשיתם עם מספרים קטנים: 7 + 8 = 15.", "445"));
    }

    @Test
    void numbersMustComeFromTheQuestionOrTheCorrectWay() {
        // Real wrong replies (2026-11-10): "81 - 18 ... 70 ... 62", "9, 12, 15" for 9 x 3.
        Tutor.Way sub = Tutor.explain("כמה זה 81 - 18?", 1);
        assertFalse(HelpText.onlyNumbers("חשבו כמה 80 גדול מ-10 (70), ואז כמה נשאר (62).", sub.numbers()));
        Tutor.Way mul = Tutor.explain("כמה זה 9 × 3?", 0);
        assertFalse(HelpText.onlyNumbers("הוסיפו 3 ל-9 שוב ושוב: 9, 12, 15.", mul.numbers()));
        assertTrue(HelpText.onlyNumbers("יש 9 קבוצות, ובכל קבוצה 3.", mul.numbers()));
    }

    @Test
    void numberWordsAndDroppedStepsAreBlocked() {
        // Real (2026-11-10): "1 + 18 ... אחת-עשרה, שתים-עשרה" - counted in words, wrongly.
        assertFalse(HelpText.safe("מתחילים מ-18 וסופרים: אחת-עשרה, שתים-עשרה...", "19"));
        String way = Tutor.explain("כמה זה 4 × 8?", 1).text();
        assertFalse(HelpText.keepsNumbers("דמיינו שאתם קופצים 4 פעמים קדימה, ואז 8 פעמים ימינה.", way));
    }

    @Test
    void codeFirstThenTheModel() {
        HelpService off = new HelpService(false, "http://localhost:1", "x");
        assertEquals("tutor", off.explain("כמה זה 4 × 8?", "32", null, "G3", 0, "רמז").source());
        String first = off.explain("כמה זה 4 × 8?", "32", null, "G3", 0, "רמז").text();
        String second = off.explain("כמה זה 4 × 8?", "32", null, "G3", 1, "רמז").text();
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
        // From the third press the model is asked; when it's off, the code's way is still there.
        assertEquals("tutor", off.explain("כמה זה 4 × 8?", "32", null, "G3", 2, "רמז").source());
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
        // A question the tutor knows -> its correct step-by-step way.
        HelpService.Help h = off.explain("כמה זה 3 + 4?", "7", null, "G1", false, "רמז");
        assertEquals("tutor", h.source());
        assertFalse(h.text().contains("7"));
        // One it doesn't (island text) -> the game's own hint.
        HelpService.Help g = off.explain("הדרקון רוצה 7 תותים", "7", null, "G1", false, "סופרים תות אחד בכל פעם");
        assertEquals("hint", g.source());
        assertEquals("סופרים תות אחד בכל פעם", g.text());
        HelpService unreachable = new HelpService(true, "http://localhost:1", "x");
        assertEquals("tutor", unreachable.explain("כמה זה 3 + 4?", "7", null, "G1", false, "רמז").source());
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
