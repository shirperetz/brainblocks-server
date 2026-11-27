package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-27 (Shir: idea #6 - badges + collection): one row per badge a child has already SEEN.
 * Which badges are earned is computed live from the child's data (BadgeService); this table only
 * remembers that the "new badge!" popup was already shown, so it shows once.
 */
@Entity
@Table(name = "learn_badge")
public class LearnBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    @Column(length = 40)
    private String badgeKey;

    private LocalDateTime seenAt;

    public LearnBadge() {}

    public LearnBadge(Long profileId, String badgeKey, LocalDateTime seenAt) {
        this.profileId = profileId;
        this.badgeKey = badgeKey;
        this.seenAt = seenAt;
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getBadgeKey() { return badgeKey; }
    public LocalDateTime getSeenAt() { return seenAt; }
}
