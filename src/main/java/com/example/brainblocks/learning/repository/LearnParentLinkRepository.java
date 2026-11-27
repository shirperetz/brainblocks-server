package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnParentLink;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LearnParentLinkRepository extends JpaRepository<LearnParentLink, Long> {
    Optional<LearnParentLink> findByCodeHash(String codeHash);
    List<LearnParentLink> findByProfileId(Long profileId);
    @Transactional
    long deleteByProfileId(Long profileId);
}
