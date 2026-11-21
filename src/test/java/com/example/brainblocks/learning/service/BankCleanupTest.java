package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnQuestion;
import com.example.brainblocks.learning.repository.LearnQuestionRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** 2026-11-21: the daily cleanup drops only old served questions. */
@SpringBootTest(properties = {
        "DATABASE_URL=jdbc:h2:mem:bankclean;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=", "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org"})
@ActiveProfiles("cloud")
class BankCleanupTest {
    @Autowired LearnQuestionRepository bank;

    @Test
    void onlyOldQuestionsGo() {
        LearnQuestion fresh = bank.save(new LearnQuestion(1L, "ADDITION", 1, "G3", "1 + 1", List.of("2", "3"), "2", null, null));
        bank.save(new LearnQuestion(1L, "ADDITION", 1, "G3", "2 + 2", List.of("4", "5"), "4", null, null));
        long before = bank.count();
        assertThat(bank.deleteOlderThan(LocalDateTime.now().minusDays(30))).isZero();
        assertThat(bank.deleteOlderThan(LocalDateTime.now().plusMinutes(1))).isEqualTo((int) before);
        assertThat(bank.findById(fresh.getId())).isEmpty();
    }
}
