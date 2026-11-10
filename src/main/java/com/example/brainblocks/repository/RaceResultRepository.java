package com.example.brainblocks.repository;

import com.example.brainblocks.model.RaceResult;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {

    List<RaceResult> findByRaceRoomOrderByFinalRankAsc(RaceRoom raceRoom);

    Optional<RaceResult> findByRaceRoomAndParticipantId(RaceRoom raceRoom, Long participantId);
}
