package com.example.brainblocks.repository;

import com.example.brainblocks.model.PlayerStatus;
import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RacePlayerRepository extends JpaRepository<RacePlayer, Long> {

    int countByRaceRoom(RaceRoom raceRoom);

    int countByRaceRoomAndStatus(RaceRoom raceRoom, PlayerStatus status);

    boolean existsByRaceRoomAndDisplayNameIgnoreCase(RaceRoom raceRoom, String displayName);

    List<RacePlayer> findByRaceRoomOrderByJoinedAtAsc(RaceRoom raceRoom);

    List<RacePlayer> findByRaceRoomAndStatusOrderByPositionDesc(RaceRoom raceRoom, PlayerStatus status);

    Optional<RacePlayer> findByRaceRoomAndId(RaceRoom raceRoom, Long id);
}
