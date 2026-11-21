package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.controller.LearnController;
import com.example.brainblocks.learning.dto.request.AnswerDto;
import com.example.brainblocks.learning.dto.request.SubmitSessionRequest;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerAnswer;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.SessionMode;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.repository.LearnQuestionRepository;
import com.example.brainblocks.learning.repository.LearnerAnswerRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.repository.LearnerStageProgressRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-21 (Shir chose 3 ideas for children who really struggle): calm mode (two options while a
 * topic is hard) and finding the gap (a base skill under a topic that goes badly).
 */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:struggle;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class StrugglingKidsTest {
    @Autowired LearnController learn;
    @Autowired ProgressService progress;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnerAnswerRepository answers;
    @Autowired LearnQuestionRepository bank;
    @Autowired LearnerStageProgressRepository stages;

    private LearnerProfile kid(Grade g, boolean calm) {
        LearnerProfile p = new LearnerProfile("kid", Avatar.values()[0]);
        p.setGrade(g);
        p.setCalm(calm);
        return profiles.save(p);
    }

    private void answered(Long id, Topic t, String skill, int right, int wrong) {
        List<LearnerAnswer> rows = new ArrayList<>();
        for (int i = 0; i < right + wrong; i++) {
            rows.add(new LearnerAnswer(id, t, skill, 1, SessionMode.STAGE, i < right, false, "q" + i, "1", "1"));
        }
        answers.saveAll(rows);
    }

    @Test
    void calmChildGetsTwoOptionsWhileTheTopicIsHardAndFourWhenItGoesWell() {
        LearnerProfile p = kid(Grade.G3, true);
        List<QuestionResponse> qs = learn.getQuestions(p.getId(), "ADDITION", 1, 5, true, 0, null, p.getId());
        assertThat(qs).hasSize(5).allSatisfy(q -> assertThat(q.getOptions()).hasSize(2));
        // the bank holds exactly what she saw, and the right answer is one of the two
        for (QuestionResponse q : qs) {
            var row = bank.findById(q.getBankId()).orElseThrow();
            assertThat(row.optionList()).containsExactlyElementsOf(q.getOptions());
            assertThat(q.getOptions()).contains(row.getCorrectAnswer());
        }
        answered(p.getId(), Topic.ADDITION, "ADD_NO_CARRY", 9, 1); // going well now
        assertThat(learn.getQuestions(p.getId(), "ADDITION", 1, 5, true, 0, null, p.getId()))
                .allSatisfy(q -> assertThat(q.getOptions()).hasSize(4));
    }

    @Test
    void notCalmAndTheSixtySecondsStayFour() {
        LearnerProfile p = kid(Grade.G3, false);
        assertThat(learn.getQuestions(p.getId(), "ADDITION", 1, 5, true, 0, null, p.getId()))
                .allSatisfy(q -> assertThat(q.getOptions()).hasSize(4));
        LearnerProfile c = kid(Grade.G3, true);
        assertThat(learn.getQuestions(c.getId(), "ADDITION", 1, 5, false, 0, null, c.getId()))
                .allSatisfy(q -> assertThat(q.getOptions()).hasSize(4));
    }

    @Test
    void twoOfKeepsTheRightAnswerAndTheClosestNumber() {
        var q = new QuestionResponse(1, Topic.ADDITION, "כמה זה 7 + 5?", List.of("2", "12", "13", "75"), 1, "", "", null);
        for (int i = 0; i < 20; i++) {
            var two = CalmMode.twoOf(q, new Random(i));
            assertThat(two.getOptions()).containsExactlyInAnyOrder("12", "13");
            assertThat(two.getOptions().get(two.getCorrectIndex())).isEqualTo("12");
        }
    }

    @Test
    void aWeakTopicFindsItsBaseCheckThenPracticeThenNothing() {
        LearnerProfile p = kid(Grade.G5, false);
        assertThat(learn.foundation(p.getId(), p.getId())).isEmpty(); // nothing played yet
        answered(p.getId(), Topic.DIVISION, "DIV_BIG", 4, 8); // division goes badly
        Map<String, Object> gap = learn.foundation(p.getId(), p.getId());
        assertThat(gap).containsEntry("key", "TIMES").containsEntry("status", "CHECK").containsEntry("forTopic", "DIVISION");
        answered(p.getId(), Topic.MULTIPLICATION, "BASE_TIMES", 2, 4); // the check: the times table is weak
        assertThat(learn.foundation(p.getId(), p.getId())).containsEntry("key", "TIMES").containsEntry("status", "PRACTICE");
        answered(p.getId(), Topic.MULTIPLICATION, "BASE_TIMES", 10, 0); // practiced - strong now
        // the next base under division is checked next
        assertThat(learn.foundation(p.getId(), p.getId())).containsEntry("key", "DIVIDE").containsEntry("status", "CHECK");
        answered(p.getId(), Topic.DIVISION, "BASE_DIVIDE", 9, 1);
        assertThat(learn.foundation(p.getId(), p.getId())).isEmpty();
    }

    @Test
    void aFoundationsRoundIsTheBaseAndDoesNotTouchTheMap() {
        LearnerProfile p = kid(Grade.G5, false);
        List<QuestionResponse> qs = learn.getQuestions(p.getId(), "ADDITION", 1, 5, true, 0, "TIMES", p.getId());
        assertThat(qs).hasSize(5).allSatisfy(q -> {
            assertThat(q.getSkill()).isEqualTo("BASE_TIMES");
            assertThat(q.getTopic()).isEqualTo(Topic.MULTIPLICATION);
        });
        // within the times table: both factors 2..10
        for (QuestionResponse q : qs) {
            var row = bank.findById(q.getBankId()).orElseThrow();
            for (String n : row.getText().replaceAll("[^0-9 ]", " ").trim().split("\\s+")) {
                if (!n.isBlank() && !row.getText().contains("=")) assertThat(Integer.parseInt(n)).isBetween(2, 10);
            }
        }
        List<AnswerDto> sent = new ArrayList<>();
        for (QuestionResponse q : qs) {
            var row = bank.findById(q.getBankId()).orElseThrow();
            learn.answerQuestion(q.getBankId(), Map.of("answer", row.getCorrectAnswer()), p.getId());
            AnswerDto a = new AnswerDto();
            a.setBankId(q.getBankId());
            a.setSkill(q.getSkill());
            a.setTopic(q.getTopic().name());
            a.setChosenAnswer(row.getCorrectAnswer());
            sent.add(a);
        }
        SubmitSessionRequest r = new SubmitSessionRequest();
        r.setProfileId(p.getId());
        r.setTopic("MULTIPLICATION");
        r.setStage(6);
        r.setMode("STAGE");
        r.setFoundation("TIMES");
        r.setAnswers(sent);
        var s = progress.submitSession(r);
        assertThat(s.getCorrect()).isEqualTo(5);
        assertThat(stages.findByProfileIdAndTopic(p.getId(), Topic.MULTIPLICATION)).isEmpty();
        assertThat(answers.findTop600ByProfileIdOrderByIdDesc(p.getId())).allSatisfy(a -> assertThat(a.getSkill()).isEqualTo("BASE_TIMES"));
    }
}
