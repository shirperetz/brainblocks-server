package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.HelpCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelpCacheRepository extends JpaRepository<HelpCacheEntry, Long> {
    Optional<HelpCacheEntry> findByCacheKey(String cacheKey);
}
