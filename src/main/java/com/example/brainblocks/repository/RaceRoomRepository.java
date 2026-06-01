package com.example.brainblocks.repository;

import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RaceRoomRepository extends JpaRepository<RaceRoom, Long> {

    Optional<RaceRoom> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);
}
