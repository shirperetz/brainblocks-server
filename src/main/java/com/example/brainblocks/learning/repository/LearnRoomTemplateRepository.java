package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnRoomTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnRoomTemplateRepository extends JpaRepository<LearnRoomTemplate, Long> {
    List<LearnRoomTemplate> findByTeacherIdOrderByIdDesc(Long teacherId);

    long countByTeacherId(Long teacherId);
}
