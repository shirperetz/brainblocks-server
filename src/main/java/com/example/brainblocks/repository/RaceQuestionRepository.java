package com.example.brainblocks.repository;

import com.example.brainblocks.model.RacePlayer;
import com.example.brainblocks.model.RaceQuestion;
import com.example.brainblocks.model.RaceRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RaceQuestionRepository extends JpaRepository<RaceQuestion, Long> {

    Optional<RaceQuestion> findByParticipantAndAnsweredFalse(RacePlayer participant);

    List<RaceQuestion> findByRaceRoomOrderByCreatedAtAsc(RaceRoom raceRoom);

    List<RaceQuestion> findByParticipantOrderByCreatedAtAsc(RacePlayer participant);

    int countByParticipantAndAnsweredTrue(RacePlayer participant);
}
