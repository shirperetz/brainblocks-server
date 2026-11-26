package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LearnTokenRepository extends JpaRepository<LearnToken, Long> {
    Optional<LearnToken> findByTokenHash(String tokenHash);
    List<LearnToken> findByProfileIdOrderByUsedAtDesc(Long profileId);
    @Transactional
    long deleteByProfileId(Long profileId);
}
