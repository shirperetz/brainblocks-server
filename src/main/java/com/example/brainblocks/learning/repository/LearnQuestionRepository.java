package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

/** 2026-11-12: the bank of every question served to every child. */
public interface LearnQuestionRepository extends JpaRepository<LearnQuestion, Long> {

    /** 2026-11-21: questions older than [before] are only ever read to judge a fresh answer - safe to drop. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("delete from LearnQuestion q where q.createdAt < :before")
    int deleteOlderThan(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);

}
