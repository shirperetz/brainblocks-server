package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnIsleProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnIsleProgressRepository extends JpaRepository<LearnIsleProgress, Long> {
    List<LearnIsleProgress> findByProfileId(Long profileId);

    Optional<LearnIsleProgress> findByProfileIdAndIsleKeyAndTopicAndStageNumber(Long profileId, String isleKey, String topic, int stageNumber);
}
