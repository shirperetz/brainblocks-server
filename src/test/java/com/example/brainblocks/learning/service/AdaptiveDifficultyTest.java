package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** 2026-11-12 (idea #3): the numbers grow when the child gets everything right, shrink when it struggles. */
class AdaptiveDifficultyTest {

    private static List<LearnerAnswer> answers(Topic topic, int right, int wrong, SessionMode mode) {
        List<LearnerAnswer> list = new ArrayList<>();
        LocalDateTime t = LocalDateTime.of(2026, 11, 12, 10, 0);
        for (int i = 0; i < right + wrong; i++) {
            list.add(new LearnerAnswer(1L, topic, "ADD_CARRY", 2, mode, i < right, false, "q", "1", "1").at(t.plusMinutes(i)));
        }
        return list;
    }

    @Test
    void harderWhenAlmostEverythingIsRight() {
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.ADDITION, 19, 1, SessionMode.STAGE)))
                .isEqualTo(Difficulty.HARD);
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.EASY, Topic.ADDITION, answers(Topic.ADDITION, 10, 0, SessionMode.STAGE)))
                .isEqualTo(Difficulty.MEDIUM);
    }

    @Test
    void easierWhenItIsHard() {
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.ADDITION, 5, 7, SessionMode.STAGE)))
                .isEqualTo(Difficulty.EASY);
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.HARD, Topic.ADDITION, answers(Topic.ADDITION, 4, 8, SessionMode.STAGE)))
                .isEqualTo(Difficulty.MEDIUM);
    }

    @Test
    void unchangedInTheMiddle_withFewAnswers_otherTopics_orThe60SecondRace() {
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.ADDITION, 15, 5, SessionMode.STAGE)))
                .isEqualTo(Difficulty.MEDIUM);
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.ADDITION, 8, 0, SessionMode.STAGE)))
                .isEqualTo(Difficulty.MEDIUM);
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.SUBTRACTION, 20, 0, SessionMode.STAGE)))
                .isEqualTo(Difficulty.MEDIUM);
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, answers(Topic.ADDITION, 0, 20, SessionMode.TIMED)))
                .isEqualTo(Difficulty.MEDIUM);
    }

    @Test
    void onlyTheRecentAnswersCount_soAChildWhoImprovedMovesUp() {
        List<LearnerAnswer> list = new ArrayList<>(answers(Topic.ADDITION, 0, 30, SessionMode.STAGE)); // long ago: all wrong
        LocalDateTime later = LocalDateTime.of(2026, 11, 13, 10, 0);
        for (int i = 0; i < 20; i++) {
            list.add(new LearnerAnswer(1L, Topic.ADDITION, "ADD_CARRY", 2, SessionMode.STAGE, true, false, "q", "1", "1").at(later.plusMinutes(i)));
        }
        assertThat(AdaptiveDifficulty.forTopic(Difficulty.MEDIUM, Topic.ADDITION, list)).isEqualTo(Difficulty.HARD);
    }

    private static final Pattern PLUS = Pattern.compile("כמה זה (\\d+) \\+ (\\d+)\\?");

    @Test
    void harderReallyMeansBiggerNumbers() {
        LearningQuestionService gen = new LearningQuestionService();
        double easy = averageSum(gen, Difficulty.EASY);
        double hard = averageSum(gen, Difficulty.HARD);
        assertThat(hard).as("hard %.1f vs easy %.1f", hard, easy).isGreaterThan(easy * 1.2);
    }

    private static double averageSum(LearningQuestionService gen, Difficulty d) {
        double sum = 0;
        int n = 0;
        for (int i = 0; i < 60; i++) {
            for (QuestionResponse q : gen.generate(Topic.ADDITION, Grade.G3, d, 3, 10)) {
                Matcher m = PLUS.matcher(q.getText());
                if (m.find()) {
                    sum += Integer.parseInt(m.group(1)) + Integer.parseInt(m.group(2));
                    n++;
                }
            }
        }
        return sum / n;
    }
}
