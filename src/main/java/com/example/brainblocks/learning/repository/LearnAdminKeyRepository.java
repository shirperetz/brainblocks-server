package com.example.brainblocks.learning.repository;

import com.example.brainblocks.learning.model.LearnAdminKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearnAdminKeyRepository extends JpaRepository<LearnAdminKey, Long> {
    List<LearnAdminKey> findByProfileIdOrderByIdAsc(Long profileId);
    Optional<LearnAdminKey> findByCredentialId(String credentialId);
}
