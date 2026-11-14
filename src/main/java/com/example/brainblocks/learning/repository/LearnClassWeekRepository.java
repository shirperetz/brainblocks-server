package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnClassWeek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnClassWeekRepository extends JpaRepository<LearnClassWeek, Long> {
    List<LearnClassWeek> findByClassIdOrderByWeekStartAsc(Long classId);
}
