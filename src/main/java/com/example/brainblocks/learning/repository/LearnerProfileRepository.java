package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {

    /** 2026-11-21 (security pass): spending coins locks the child's row, so two taps can't spend twice. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from LearnerProfile p where p.id = :id")
    java.util.Optional<LearnerProfile> lockById(@org.springframework.data.repository.query.Param("id") Long id);


    java.util.Optional<LearnerProfile> findByUsername(String username);

    java.util.Optional<LearnerProfile> findByAuthToken(String authToken);

    boolean existsByUsername(String username);
}
