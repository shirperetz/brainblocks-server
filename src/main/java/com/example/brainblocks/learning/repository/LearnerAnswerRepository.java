package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerAnswerRepository extends JpaRepository<LearnerAnswer, Long> {
    List<LearnerAnswer> findByProfileId(Long profileId);
}
