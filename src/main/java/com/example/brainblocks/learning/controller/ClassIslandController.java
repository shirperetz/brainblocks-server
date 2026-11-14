package com.example.brainblocks.learning.controller;

import com.example.brainblocks.config.LearnerAuthConfig;
import com.example.brainblocks.learning.service.ClassIslandService;
import com.example.brainblocks.learning.service.LearnerProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/** 2026-11-14: the class's 3D island and the league between classes. */
@RestController
@RequestMapping("/api/learn")
public class ClassIslandController {

    private final ClassIslandService islands;
    private final LearnerProfileService profiles;

    public ClassIslandController(ClassIslandService islands, LearnerProfileService profiles) {
        this.islands = islands;
        this.profiles = profiles;
    }

    @GetMapping("/profiles/{id}/class-island")
    public Map<String, Object> mine(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        if (!id.equals(me)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found");
        return islands.forChild(profiles.findProfile(id));
    }

    @GetMapping("/staff/classes/{id}/island")
    public Map<String, Object> staff(@PathVariable Long id, @RequestAttribute(LearnerAuthConfig.ATTRIBUTE) Long me) {
        return islands.forStaff(me, id);
    }
}
