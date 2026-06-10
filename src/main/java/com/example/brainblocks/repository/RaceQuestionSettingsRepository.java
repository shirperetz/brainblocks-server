package com.example.brainblocks.repository;

import com.example.brainblocks.model.RaceQuestionSettings;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RaceQuestionSettingsRepository extends JpaRepository<RaceQuestionSettings, Long> {

    Optional<RaceQuestionSettings> findByRaceRoom(RaceRoom raceRoom);
}
