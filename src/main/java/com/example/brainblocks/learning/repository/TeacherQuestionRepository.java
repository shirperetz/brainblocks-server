package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.TeacherQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeacherQuestionRepository extends JpaRepository<TeacherQuestion, Long> {

    List<TeacherQuestion> findByTeacherIdOrderByIdDesc(Long teacherId);
}
