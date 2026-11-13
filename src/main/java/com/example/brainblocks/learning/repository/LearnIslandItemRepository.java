package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnIslandItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnIslandItemRepository extends JpaRepository<LearnIslandItem, Long> {
    List<LearnIslandItem> findByProfileId(Long profileId);
}
