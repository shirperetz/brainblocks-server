package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 2026-11-14 (Shir: "another password or my fingerprint" for the admin on the public site): a passkey -
 * the fingerprint / face / PIN of one of her devices. Only its public key is kept; the secret part never
 * leaves the device.
 */
@Entity
@Table(name = "learn_admin_key")
public class LearnAdminKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long profileId;

    /** The credential id the device gave (base64url). */
    @Column(length = 400, unique = true)
    private String credentialId;

    /** SubjectPublicKeyInfo (DER), base64. */
    @Column(length = 1200)
    private String publicKey;

    /** COSE algorithm: -7 = ES256, -257 = RS256. */
    private int alg;

    private long signCount;

    @Column(length = 40)
    private String name;

    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;

    protected LearnAdminKey() {
    }

    public LearnAdminKey(Long profileId, String credentialId, String publicKey, int alg, long signCount, String name) {
        this.profileId = profileId;
        this.credentialId = credentialId;
        this.publicKey = publicKey;
        this.alg = alg;
        this.signCount = signCount;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getProfileId() { return profileId; }
    public String getCredentialId() { return credentialId; }
    public String getPublicKey() { return publicKey; }
    public int getAlg() { return alg; }
    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
