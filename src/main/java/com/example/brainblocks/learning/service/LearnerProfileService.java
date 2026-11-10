package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.request.CreateProfileRequest;
import com.example.brainblocks.learning.dto.request.UpdateProfileRequest;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.Difficulty;
import com.example.brainblocks.learning.model.Grade;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class LearnerProfileService {

    private final LearnerProfileRepository profileRepository;

    public LearnerProfileService(LearnerProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    public ProfileResponse createProfile(CreateProfileRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Name is required");
        }
        if (name.length() > 24) {
            name = name.substring(0, 24);
        }
        Avatar avatar = parseEnum(Avatar.class, request.getAvatar(), "avatar");
        LearnerProfile profile = new LearnerProfile(name, avatar);
        return ProfileResponse.fromEntity(profileRepository.save(profile));
    }

    public ProfileResponse getProfile(Long id) {
        return ProfileResponse.fromEntity(findProfile(id));
    }

    public ProfileResponse updateProfile(Long id, UpdateProfileRequest request) {
        LearnerProfile profile = findProfile(id);
        if (request.getAvatar() != null) {
            profile.setAvatar(parseEnum(Avatar.class, request.getAvatar(), "avatar"));
        }
        if (request.getGrade() != null) {
            profile.setGrade(parseEnum(Grade.class, request.getGrade(), "grade"));
        }
        if (request.getDifficulty() != null) {
            profile.setDifficulty(parseEnum(Difficulty.class, request.getDifficulty(), "difficulty"));
        }
        if (request.getSoundEnabled() != null) {
            profile.setSoundEnabled(request.getSoundEnabled());
        }
        profile.touch();
        return ProfileResponse.fromEntity(profileRepository.save(profile));
    }

    /** Loads a profile or throws 404. Shared by other learning services. */
    public LearnerProfile findProfile(Long id) {
        return profileRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Profile not found"));
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String fieldName) {
        if (value == null) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid " + fieldName + ": " + value);
        }
    }
}
