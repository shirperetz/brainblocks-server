package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnContactRepository extends JpaRepository<LearnContact, Long> {
    List<LearnContact> findTop200ByOrderByIdDesc();
}
