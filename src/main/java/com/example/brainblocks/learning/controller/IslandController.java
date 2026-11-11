package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.AccessService;
import com.example.brainblocks.learning.service.IslandService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** 2026-11-11 "האי שלי": the child's own island (read + choose), and a read-only view for her teacher / the admin. */
@RestController
public class IslandController {

    private final IslandService island;
    private final AccessService access;

    public IslandController(IslandService island, AccessService access) {
        this.island = island;
        this.access = access;
    }

    @GetMapping("/api/learn/profiles/{id}/island")
    public Map<String, Object> mine(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return island.island(id);
    }

    /** body: { spot: "ADDITION-3", item: "🌳" } */
    @PutMapping("/api/learn/profiles/{id}/island")
    public Map<String, Object> choose(@PathVariable Long id, @RequestBody Map<String, String> body,
                                      @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        own(id, me);
        return island.choose(id, body.get("spot"), body.get("item"));
    }

    @GetMapping("/api/learn/staff/children/{id}/island")
    public Map<String, Object> childIsland(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        access.requireStaff(me);
        access.requireCanSee(me, id);
        return island.island(id);
    }

    private static void own(Long id, Long me) {
        if (id == null || !id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
    }
}
