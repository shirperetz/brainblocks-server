package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, Long> {
}
