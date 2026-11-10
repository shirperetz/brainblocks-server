package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerStageProgress;
import com.example.brainblocks.learning.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnerStageProgressRepository extends JpaRepository<LearnerStageProgress, Long> {

    List<LearnerStageProgress> findByProfileIdAndTopic(Long profileId, Topic topic);

    List<LearnerStageProgress> findByProfileId(Long profileId);

    Optional<LearnerStageProgress> findByProfileIdAndTopicAndStageNumber(Long profileId, Topic topic, int stageNumber);
}
