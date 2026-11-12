package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

/** 2026-11-12: the bank of every question served to every child. */
public interface LearnQuestionRepository extends JpaRepository<LearnQuestion, Long> {
}
