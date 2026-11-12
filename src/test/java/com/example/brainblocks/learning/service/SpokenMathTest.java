package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.Topic;
import org.junit.jupiter.api.Test;

/** 2026-11-12: the voice skips digits - every question is read as words. */
class SpokenMathTest {

    @Test
    void numbersAsATeacherSaysThem() {
        assertThat(SpokenMath.number(0)).isEqualTo("אפס");
        assertThat(SpokenMath.number(7)).isEqualTo("שבע");
        assertThat(SpokenMath.number(12)).isEqualTo("שתים עשרה");
        assertThat(SpokenMath.number(20)).isEqualTo("עשרים");
        assertThat(SpokenMath.number(38)).isEqualTo("שלושים ושמונה");
        assertThat(SpokenMath.number(100)).isEqualTo("מאה");
        assertThat(SpokenMath.number(105)).isEqualTo("מאה וחמש");
        assertThat(SpokenMath.number(120)).isEqualTo("מאה ועשרים");
        assertThat(SpokenMath.number(123)).isEqualTo("מאה עשרים ושלוש");
        assertThat(SpokenMath.number(250)).isEqualTo("מאתיים וחמישים");
        assertThat(SpokenMath.number(1000)).isEqualTo("אלף");
        assertThat(SpokenMath.number(1050)).isEqualTo("אלף וחמישים");
        assertThat(SpokenMath.number(3215)).isEqualTo("שלושת אלפים מאתיים וחמש עשרה");
        assertThat(SpokenMath.number(45000)).isEqualTo("ארבעים וחמש אלף");
    }

    @Test
    void questionsReadAloud() {
        assertThat(SpokenMath.toSpeech("כמה זה 38 + 27?")).isEqualTo("כמה זה שלושים ושמונה ועוד עשרים ושבע?");
        assertThat(SpokenMath.toSpeech("כמה זה 42 - 17?")).isEqualTo("כמה זה ארבעים ושתיים פחות שבע עשרה?");
        assertThat(SpokenMath.toSpeech("כמה זה 7 × 8?")).isEqualTo("כמה זה שבע כפול שמונה?");
        assertThat(SpokenMath.toSpeech("כמה זה 56 ÷ 8?")).isEqualTo("כמה זה חמישים ושש חלקי שמונה?");
        assertThat(SpokenMath.toSpeech("מה המספר החסר? 7 + ? = 12")).isEqualTo("מה המספר החסר? שבע ועוד כמה שווה שתים עשרה");
        assertThat(SpokenMath.toSpeech("מה המספר הבא? 20, 17, 14, 11, ?")).isEqualTo("מה המספר הבא? עשרים, שבע עשרה, ארבע עשרה, אחת עשרה, כמה");
        assertThat(SpokenMath.toSpeech("צמצמו את השבר 6/8")).isEqualTo("צמצמו את השבר שש שמיניות");
        assertThat(SpokenMath.toSpeech("17/5 = כמה שלמים?")).isEqualTo("שבע עשרה חלקי חמש שווה כמה שלמים?");
        assertThat(SpokenMath.toSpeech("זווית של 120° היא זווית...")).isEqualTo("זווית של מאה ועשרים מעלות היא זווית...");
        assertThat(SpokenMath.toSpeech("0.5 + 0.25")).isEqualTo("אפס נקודה חמש ועוד אפס נקודה שתיים חמש");
    }

    @Test
    void noDigitIsLeftInAnyGeneratedQuestion() {
        LearningQuestionService gen = new LearningQuestionService();
        for (Topic topic : Topic.values()) {
            if (topic == Topic.DRAGON || topic == Topic.BALLOONS || topic == Topic.BRIDGE || topic == Topic.TRAIN) continue;
            for (Grade grade : Grade.values()) {
                for (int stage = 1; stage <= 6; stage++) {
                    for (QuestionResponse q : gen.generate(topic, grade, Difficulty.MEDIUM, stage, 10)) {
                        assertThat(SpokenMath.toSpeech(q.getText())).as(q.getText()).doesNotContainPattern("\\d");
                    }
                }
            }
        }
    }
}
