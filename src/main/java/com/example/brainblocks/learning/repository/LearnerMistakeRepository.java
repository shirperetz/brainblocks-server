package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerMistake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerMistakeRepository extends JpaRepository<LearnerMistake, Long> {

    List<LearnerMistake> findByProfileIdAndResolvedFalseOrderByCreatedAtDesc(Long profileId);

    List<LearnerMistake> findByProfileId(Long profileId);
}
