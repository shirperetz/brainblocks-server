package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnerSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnerSessionRepository extends JpaRepository<LearnerSession, Long> {

    List<LearnerSession> findByProfileId(Long profileId);
}
