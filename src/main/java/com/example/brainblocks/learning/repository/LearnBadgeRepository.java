package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnBadge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnBadgeRepository extends JpaRepository<LearnBadge, Long> {
    List<LearnBadge> findByProfileId(Long profileId);
}
