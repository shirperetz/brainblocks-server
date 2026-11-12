package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerAnswerRepository extends JpaRepository<LearnerAnswer, Long> {
    List<LearnerAnswer> findByProfileId(Long profileId);

    /** 2026-11-12 (idea #6, the class building): a child's right answers since the week began. */
    long countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(Long profileId, java.time.LocalDateTime since);
}
