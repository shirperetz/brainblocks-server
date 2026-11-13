package com.example.brainblocks.learning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 2026-11-14: a message to the site's operator (privacy, accessibility, deletion, a problem). */
@Entity
@Table(name = "learn_contact")
public class LearnContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 20)
    private String kind;

    @Column(length = 60)
    private String name;

    /** How to answer (email / phone) - only what the writer chose to give. */
    @Column(length = 120)
    private String reply;

    @Column(length = 2000)
    private String text;

    /** The account that sent it, when someone was logged in. */
    private Long profileId;

    private LocalDateTime at;

    private boolean done;

    protected LearnContact() {}

    public LearnContact(String kind, String name, String reply, String text, Long profileId) {
        this.kind = kind;
        this.name = name;
        this.reply = reply;
        this.text = text;
        this.profileId = profileId;
        this.at = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getKind() { return kind; }
    public String getName() { return name; }
    public String getReply() { return reply; }
    public String getText() { return text; }
    public Long getProfileId() { return profileId; }
    public LocalDateTime getAt() { return at; }
    public boolean isDone() { return done; }
    public void setDone(boolean done) { this.done = done; }
}
