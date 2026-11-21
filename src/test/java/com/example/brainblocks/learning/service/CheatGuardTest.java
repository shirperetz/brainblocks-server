package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.brainblocks.learning.dto.request.AnswerDto;
import com.example.brainblocks.learning.dto.request.SubmitSessionRequest;
import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnQuestion;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnQuestionRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * 2026-11-21 (security pass, Shir: "do all of them"): a child who knows a little about computers
 * tries to get stars / coins without playing. Every trick must fail; a real round must still count.
 */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:cheat;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class CheatGuardTest {
    @Autowired ProgressService progress;
    @Autowired LearnerProfileRepository profiles;
    @Autowired LearnQuestionRepository bank;

    private final AtomicLong now = new AtomicLong(1_000_000);

    @AfterEach
    void restoreClock() {
        GameResultGuard.clock = System::currentTimeMillis;
        GameResultGuard.resetForTests();
    }

    private Long kid() {
        return profiles.save(new LearnerProfile("kid", Avatar.values()[0])).getId();
    }

    private static AnswerDto ans(String chosen, String right, boolean claimed) {
        AnswerDto a = new AnswerDto();
        a.setQuestionText("2 + 3");
        a.setChosenAnswer(chosen);
        a.setCorrectAnswer(right);
        a.setCorrect(claimed);
        return a;
    }

    private SubmitSessionRequest round(Long id, String topic, List<AnswerDto> answers) {
        SubmitSessionRequest r = new SubmitSessionRequest();
        r.setProfileId(id);
        r.setTopic(topic);
        r.setStage(1);
        r.setMode("STAGE");
        r.setDurationMs(60_000);
        r.setAnswers(answers);
        return r;
    }

    @Test
    void questionTopicWithoutBankQuestionsGivesNothing() {
        Long id = kid();
        List<AnswerDto> fake = new ArrayList<>();
        for (int i = 0; i < 10; i++) fake.add(ans("5", "5", true));
        var s = progress.submitSession(round(id, "ADDITION", fake));
        assertThat(s.getCorrect()).isZero();
        assertThat(s.getStars()).isZero();
    }

    @Test
    void oneBankQuestionCountsOnceAndAnotherChildsQuestionNever() {
        Long id = kid();
        Long other = kid();
        LearnQuestion mine = bank.save(new LearnQuestion(id, "ADDITION", 1, "G3", "2 + 3", List.of("5", "6"), "5", null, null));
        LearnQuestion theirs = bank.save(new LearnQuestion(other, "ADDITION", 1, "G3", "2 + 3", List.of("5", "6"), "5", null, null));
        List<AnswerDto> list = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            AnswerDto a = ans("5", "5", true);
            a.setBankId(mine.getId());
            list.add(a);
        }
        AnswerDto stolen = ans("5", "5", true);
        stolen.setBankId(theirs.getId());
        list.add(stolen);
        assertThat(progress.submitSession(round(id, "ADDITION", list)).getCorrect()).isEqualTo(1);
    }

    @Test
    void gamesNeedAFullRoundRealAnswersAndRealTime() {
        GameResultGuard.clock = now::get;
        Long id = kid();
        // one answer = 100% - refused
        assertThatThrownBy(() -> progress.submitSession(round(id, "DRAGON", List.of(ans("3", "3", true)))))
                .isInstanceOf(ResponseStatusException.class);
        // "right" with a wrong answer is not right
        List<AnswerDto> lies = new ArrayList<>();
        for (int i = 0; i < 6; i++) lies.add(ans("1", "9", true));
        assertThat(progress.submitSession(round(id, "DRAGON", lies)).getCorrect()).isZero();
        // a second round 3 seconds later (6 answers) - too fast for a child
        now.addAndGet(3_000);
        List<AnswerDto> real = new ArrayList<>();
        for (int i = 0; i < 6; i++) real.add(ans("4", "4", false));
        assertThatThrownBy(() -> progress.submitSession(round(id, "DRAGON", real))).isInstanceOf(ResponseStatusException.class);
        // a real round a minute later counts in full (the phone's flag doesn't matter, the answers do)
        now.addAndGet(60_000);
        var s = progress.submitSession(round(id, "DRAGON", real));
        assertThat(s.getCorrect()).isEqualTo(6);
        assertThat(s.getStars()).isEqualTo(3);
    }

    @Test
    void anAnswerSentInAdvanceCountsOnlyInTheSixtySecondGame() {
        Long id = kid();
        LearnQuestion seen = new LearnQuestion(id, "ADDITION", 1, "G3", "2 + 3", List.of("5", "6"), "5", null, null);
        seen.setRevealed(true);
        seen = bank.save(seen);
        List<AnswerDto> list = new ArrayList<>();
        AnswerDto a = ans("5", "5", true);
        a.setBankId(seen.getId());
        list.add(a);
        assertThat(progress.submitSession(round(id, "ADDITION", list)).getCorrect()).isZero();
        SubmitSessionRequest timed = round(id, "MIXED", List.of(a));
        timed.setMode("TIMED");
        a.setCorrect(true);
        assertThat(progress.submitSession(timed).getCorrect()).isEqualTo(1);
    }
}
