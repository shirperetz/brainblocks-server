package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearnAuditRepository extends JpaRepository<LearnAudit, Long> {
    List<LearnAudit> findTop300ByOrderByIdDesc();
}
