package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnAssignmentRepository extends JpaRepository<LearnAssignment, Long> {

    List<LearnAssignment> findByClassIdOrderByStartDateDescIdDesc(Long classId);
}
