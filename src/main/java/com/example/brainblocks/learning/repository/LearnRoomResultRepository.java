package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnRoomResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearnRoomResultRepository extends JpaRepository<LearnRoomResult, Long> {
    Optional<LearnRoomResult> findFirstByProfileIdOrderByIdDesc(Long profileId);

    boolean existsByProfileIdAndRoomKey(Long profileId, String roomKey);
}
