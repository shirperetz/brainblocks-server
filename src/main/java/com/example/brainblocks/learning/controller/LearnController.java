package com.example.brainblocks.learning.controller;

import com.example.brainblocks.learning.dto.request.CreateProfileRequest;
import com.example.brainblocks.learning.dto.request.SubmitSessionRequest;
import com.example.brainblocks.learning.dto.request.UpdateProfileRequest;
import com.example.brainblocks.learning.dto.response.MapResponse;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.dto.response.QuestionResponse;
import com.example.brainblocks.learning.dto.response.SessionSummaryResponse;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.model.Topic;
import com.example.brainblocks.learning.service.LearnerProfileService;
import com.example.brainblocks.learning.service.LearningQuestionService;
import com.example.brainblocks.learning.service.ProgressService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * REST entry point for the kids learning game ("עולם החשבון"). All endpoints live under
 * {@code /api/learn}. Kept separate from the race controllers so the two products never collide.
 */
@RestController
@RequestMapping("/api/learn")
public class LearnController {

    private final LearnerProfileService profileService;
    private final LearningQuestionService questionService;
    private final ProgressService progressService;

    public LearnController(LearnerProfileService profileService,
                           LearningQuestionService questionService,
                           ProgressService progressService) {
        this.profileService = profileService;
        this.questionService = questionService;
        this.progressService = progressService;
    }

    // ---- Profiles ----------------------------------------------------------

    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileResponse createProfile(@RequestBody CreateProfileRequest request) {
        return profileService.createProfile(request);
    }

    @GetMapping("/profiles/{id}")
    public ProfileResponse getProfile(@PathVariable Long id) {
        return profileService.getProfile(id);
    }

    @PatchMapping("/profiles/{id}")
    public ProfileResponse updateProfile(@PathVariable Long id,
                                         @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(id, request);
    }

    // ---- Gameplay ----------------------------------------------------------

    /** Generates a batch of questions for a stage, scaled by the profile's grade + difficulty. */
    @GetMapping("/questions")
    public List<QuestionResponse> getQuestions(@RequestParam Long profileId,
                                               @RequestParam String topic,
                                               @RequestParam(defaultValue = "1") int stage,
                                               @RequestParam(defaultValue = "10") int count) {
        LearnerProfile profile = profileService.findProfile(profileId);
        int clamped = Math.min(Math.max(count, 1), 20);
        return questionService.generate(parseTopic(topic), profile.getGrade(),
                profile.getDifficulty(), stage, clamped);
    }

    @GetMapping("/profiles/{id}/map")
    public MapResponse getMap(@PathVariable Long id, @RequestParam String topic) {
        return progressService.getMap(id, parseTopic(topic));
    }

    @PostMapping("/sessions")
    public SessionSummaryResponse submitSession(@RequestBody SubmitSessionRequest request) {
        return progressService.submitSession(request);
    }

    private Topic parseTopic(String value) {
        try {
            return Topic.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid topic: " + value);
        }
    }
}
