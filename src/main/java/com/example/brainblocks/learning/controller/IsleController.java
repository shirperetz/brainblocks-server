package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.dto.request.AnswerDto;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.IsleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** 2026-11-13: the endless sea of islands (docs/plan-infinite-islands.md). */
@RestController
public class IsleController {

    private final IsleService isles;
    private final AccessService access;

    public IsleController(IsleService isles, AccessService access) {
        this.isles = isles;
        this.access = access;
    }

    /** The child's islands: every open one + the next, locked (always one more waiting). */
    @GetMapping("/api/learn/profiles/{id}/isles")
    public Map<String, Object> sea(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        if (!id.equals(me)) access.requireCanSee(me, id); // the child, or her teacher / the admin
        return isles.sea(id);
    }

    /** body: { topic, stage, durationMs, answers: [AnswerDto] } - a round on a generated island. */
    @PostMapping("/api/learn/profiles/{id}/isles/{key}/sessions")
    public Map<String, Object> submit(@PathVariable Long id, @PathVariable String key, @RequestBody Body body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return isles.submit(id, key, body.topic, body.stage, body.answers, body.durationMs);
    }

    public static class Body {
        public String topic;
        public int stage;
        public long durationMs;
        public List<AnswerDto> answers;
    }
}
