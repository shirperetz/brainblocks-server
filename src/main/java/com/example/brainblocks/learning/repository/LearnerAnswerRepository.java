package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerAnswerRepository extends JpaRepository<LearnerAnswer, Long> {
    List<LearnerAnswer> findByProfileId(Long profileId);

    /** 2026-11-15 (speed): only this topic's latest answers - all the question screen needs (last 20 / 200 right). */
    List<LearnerAnswer> findTop400ByProfileIdAndTopicOrderByIdDesc(Long profileId, com.example.brainblocks.learning.model.Topic topic);

    /** 2026-11-12 (idea #6, the class building): a child's right answers since the week began. */
    long countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(Long profileId, java.time.LocalDateTime since);

    /** 2026-11-14 (the class island): right answers inside one past week. */
    long countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long profileId, java.time.LocalDateTime from,
                                                                                       java.time.LocalDateTime to);
}
