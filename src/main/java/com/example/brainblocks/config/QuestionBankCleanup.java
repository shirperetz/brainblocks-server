package com.example.brainblocks.config;

import com.example.brainblocks.learning.repository.LearnQuestionRepository;
import java.time.LocalDateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 2026-11-21 (security pass, free-tier limits): every question served to a child is kept so the server
 * can judge the answer (learn_question, ~0.5KB a row). One class practicing daily ~2.5MB/day would fill
 * Neon's free 512MB in ~7 months. A question is only needed while its round is open; the child's
 * answers (reports, mistakes) live in learn_answer and are NOT touched. Rows older than 30 days go.
 */
@Configuration
@EnableScheduling
public class QuestionBankCleanup {
    private final LearnQuestionRepository bank;
    private final int keepDays;

    public QuestionBankCleanup(LearnQuestionRepository bank, @Value("${brainblocks.question-keep-days:30}") int keepDays) {
        this.bank = bank;
        this.keepDays = Math.max(2, keepDays);
    }

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    public void clean() {
        int n = bank.deleteOlderThan(LocalDateTime.now().minusDays(keepDays));
        if (n > 0) LoggerFactory.getLogger("brainblocks.cleanup").info("removed {} served questions older than {} days", n, keepDays);
    }
}
