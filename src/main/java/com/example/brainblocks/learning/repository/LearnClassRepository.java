package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnClassRepository extends JpaRepository<LearnClass, Long> {
    List<LearnClass> findByTeacherId(Long teacherId);
    Optional<LearnClass> findByJoinCode(String joinCode);
    boolean existsByJoinCode(String joinCode);
}
