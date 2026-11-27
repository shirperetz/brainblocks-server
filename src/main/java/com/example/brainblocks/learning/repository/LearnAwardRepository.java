package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnAward;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnAwardRepository extends JpaRepository<LearnAward, Long> {
    List<LearnAward> findByProfileIdAndSeenAtIsNullOrderByCreatedAtAsc(Long profileId);
    List<LearnAward> findTop20ByProfileIdOrderByCreatedAtDesc(Long profileId);
    long countByFromIdAndCreatedAtAfter(Long fromId, LocalDateTime after);
    long countByProfileIdAndCreatedAtAfter(Long profileId, LocalDateTime after);
    long countByProfileId(Long profileId);
}
