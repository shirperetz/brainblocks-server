package com.example.brainblocks.repository;

import com.example.brainblocks.model.GameEvent;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameEventRepository extends JpaRepository<GameEvent, Long> {

    List<GameEvent> findByRaceRoomOrderByCreatedAtDesc(RaceRoom raceRoom);

    List<GameEvent> findTop20ByRaceRoomOrderByCreatedAtDesc(RaceRoom raceRoom);
}
