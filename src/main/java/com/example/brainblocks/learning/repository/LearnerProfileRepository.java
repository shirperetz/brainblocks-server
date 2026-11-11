package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {

    java.util.Optional<LearnerProfile> findByUsername(String username);

    java.util.Optional<LearnerProfile> findByAuthToken(String authToken);

    boolean existsByUsername(String username);
}
