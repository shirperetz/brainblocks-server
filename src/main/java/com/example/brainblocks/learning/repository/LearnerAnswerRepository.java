package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerAnswerRepository extends JpaRepository<LearnerAnswer, Long> {
    List<LearnerAnswer> findByProfileId(Long profileId);

    /** 2026-11-21 (finding the gap): the child's latest answers in every topic. */
    List<LearnerAnswer> findTop600ByProfileIdOrderByIdDesc(Long profileId);

    /** 2026-11-15 (speed): only this topic's latest answers - all the question screen needs (last 20 / 200 right). */
    List<LearnerAnswer> findTop400ByProfileIdAndTopicOrderByIdDesc(Long profileId, com.example.brainblocks.learning.model.Topic topic);

    /** 2026-11-12 (idea #6, the class building): a child's right answers since the week began. */
    long countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqual(Long profileId, java.time.LocalDateTime since);

    /** 2026-11-14 (the class island): right answers inside one past week. */
    long countByProfileIdAndCorrectTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long profileId, java.time.LocalDateTime from,
                                                                                       java.time.LocalDateTime to);

    /** 2026-11-22 the teacher's weekly report: the class's answers from a date on. */
    List<LearnerAnswer> findByProfileIdInAndCreatedAtGreaterThanEqual(java.util.Collection<Long> profileIds, java.time.LocalDateTime since);
}
