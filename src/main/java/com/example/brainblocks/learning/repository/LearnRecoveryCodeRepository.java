package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnRecoveryCodeRepository extends JpaRepository<LearnRecoveryCode, Long> {
    List<LearnRecoveryCode> findByProfileIdAndUsedAtIsNull(Long profileId);
    List<LearnRecoveryCode> findByProfileId(Long profileId);
}
