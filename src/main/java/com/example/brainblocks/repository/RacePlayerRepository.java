package com.example.brainblocks.repository;

import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RacePlayerRepository extends JpaRepository<RacePlayer, Long> {

    int countByRaceRoom(RaceRoom raceRoom);

    boolean existsByRaceRoomAndDisplayNameIgnoreCase(RaceRoom raceRoom, String displayName);

    List<RacePlayer> findByRaceRoomOrderByJoinedAtAsc(RaceRoom raceRoom);
}
