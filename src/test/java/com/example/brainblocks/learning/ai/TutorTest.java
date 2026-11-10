package com.example.brainblocks.learning.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.service.LearningQuestionService;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The code's explanations, on thousands of real generated questions: known kind, never the answer. */
class TutorTest {

    private final LearningQuestionService questions = new LearningQuestionService();

    @Test
    void everyGeneratedQuestionGetsTwoWaysThatNeverGiveTheAnswer() {
        int checked = 0;
        for (Topic t : List.of(Topic.ADDITION, Topic.SUBTRACTION, Topic.MULTIPLICATION, Topic.DIVISION,
                Topic.COMPARISON, Topic.SEQUENCES, Topic.WORD_PROBLEMS)) {
            for (Grade g : Grade.values()) {
                for (int stage = 1; stage <= 6; stage++) {
                    for (QuestionResponse q : questions.generate(t, g, Difficulty.HARD, stage, 15)) {
                        long answer = Long.parseLong(q.getOptions().get(q.getCorrectIndex()));
                        // Numbers that come straight from the question: itself and its place-value parts
                        // ("22 - 16": "16 = 10 + 6" shows a 6 that only happens to be the answer).
                        List<Long> given = new java.util.ArrayList<>(Tutor.numbersIn(q.getText()));
                        for (Long n : Tutor.numbersIn(q.getText())) given.addAll(Tutor.parts(n));
                        Tutor.Way w0 = Tutor.explain(q.getText(), 0);
                        Tutor.Way w1 = Tutor.explain(q.getText(), 1);
                        assertNotNull(w0, q.getText());
                        assertNotNull(w1, q.getText());
                        assertNotEquals(w0.text(), w1.text(), "'explain differently' must differ - " + q.getText());
                        for (Tutor.Way w : List.of(w0, w1)) {
                            if (!given.contains(answer)) {
                                assertFalse(Tutor.numbersIn(w.text()).contains(answer),
                                        "gives the answer away: " + q.getText() + " -> " + w.text());
                            }
                            assertTrue(HelpText.safe(w.text(), null), "fails its own safety rules: " + w.text());
                            assertFalse(HelpText.hasWrongEquation(w.text()), w.text());
                        }
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 3000);
    }

    @Test
    void placeValueSplit() {
        assertEquals("377 = 300 + 70 + 7", Tutor.split(377));
        assertEquals("704 = 700 + 4", Tutor.split(704));
        assertEquals("8", Tutor.split(8));
    }
}
