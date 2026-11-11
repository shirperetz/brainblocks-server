package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.dto.request.CreateProfileRequest;
import com.example.brainblocks.learning.dto.request.UpdateProfileRequest;
import com.example.brainblocks.learning.dto.response.ProfileResponse;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Children's accounts (2026-11-11, Shir: "a link to the math game for children, each with a username
 * and password, and it remembers each one"). A child signs up once (grade, character, name, username,
 * password) and logs in with username + password on any device; the device keeps a random token, so
 * the child stays logged in there. Progress was already saved per profile on the server.
 * <p>
 * Passwords: PBKDF2-HMAC-SHA256 with a random salt, never stored or logged as typed. Five wrong
 * passwords for a username pause that username for 5 minutes.
 */
@Service
public class AccountService {

    /** Hebrew or English letters, digits, "_" or "." - 3 to 20. */
    static final Pattern USERNAME = Pattern.compile("[\\p{IsHebrew}a-z0-9_.]{3,20}");
    static final int MIN_PASSWORD = 4;
    static final int MAX_PASSWORD = 40;
    private static final int ITERATIONS = 120_000;
    static final int MAX_FAILURES = 5;
    static final Duration LOCK = Duration.ofMinutes(5);

    private final LearnerProfileRepository repository;
    private final LearnerProfileService profileService;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Failures> failures = new ConcurrentHashMap<>();

    private record Failures(int count, Instant since) {
    }

    public record Session(String token, ProfileResponse profile) {
    }

    /**
     * 2026-11-11 (public link): a new child needs the join code Shir hands out - strangers who find
     * the link see only the log-in. Kept in local.properties (brainblocks.join-code), not in git.
     * Blank = sign-up closed.
     */
    private final String joinCode;

    public AccountService(LearnerProfileRepository repository, LearnerProfileService profileService) {
        this(repository, profileService, "");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AccountService(LearnerProfileRepository repository, LearnerProfileService profileService,
                          @org.springframework.beans.factory.annotation.Value("${brainblocks.join-code:}") String joinCode) {
        this.repository = repository;
        this.profileService = profileService;
        this.joinCode = joinCode == null ? "" : joinCode.trim();
    }

    public Session register(String joinCode, String username, String password, String name, String avatar,
                            String grade, String difficulty) {
        if (this.joinCode.isEmpty() || joinCode == null
                || !MessageDigest.isEqual(this.joinCode.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        joinCode.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "קוד ההצטרפות לא נכון. בקשו אותו מההורים או מהמורה");
        }
        return register(username, password, name, avatar, grade, difficulty);
    }

    Session register(String username, String password, String name, String avatar, String grade, String difficulty) {
        String user = normalize(username);
        if (!USERNAME.matcher(user).matches()) {
            throw bad("שם המשתמש צריך להיות 3 עד 20 אותיות או ספרות, בלי רווחים");
        }
        checkPassword(password);
        if (repository.existsByUsername(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "שם המשתמש הזה כבר תפוס - נסו שם אחר");
        }
        CreateProfileRequest create = new CreateProfileRequest();
        create.setName(name);
        create.setAvatar(avatar);
        Long id = profileService.createProfile(create).getId();
        if (grade != null && !grade.isBlank()) {
            UpdateProfileRequest update = new UpdateProfileRequest();
            update.setGrade(grade);
            update.setDifficulty(difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty);
            profileService.updateProfile(id, update);
        }
        LearnerProfile p = profileService.findProfile(id);
        p.setUsername(user);
        p.setPasswordHash(hash(password));
        p.setAuthToken(newToken());
        repository.save(p);
        return new Session(p.getAuthToken(), ProfileResponse.fromEntity(p));
    }

    public Session login(String username, String password, Instant now) {
        String user = normalize(username);
        Failures f = failures.get(user);
        if (f != null && f.count() >= MAX_FAILURES && now.isBefore(f.since().plus(LOCK))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "יותר מדי ניסיונות. נסו שוב בעוד כמה דקות");
        }
        Optional<LearnerProfile> found = repository.findByUsername(user);
        if (found.isEmpty() || password == null || !matches(password, found.get().getPasswordHash())) {
            failures.merge(user, new Failures(1, now), (old, one) ->
                    now.isAfter(old.since().plus(LOCK)) ? one : new Failures(old.count() + 1, old.since()));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "שם המשתמש או הסיסמה לא נכונים");
        }
        failures.remove(user);
        LearnerProfile p = found.get();
        if (p.getAuthToken() == null) {
            p.setAuthToken(newToken());
        }
        p.touch();
        repository.save(p);
        return new Session(p.getAuthToken(), ProfileResponse.fromEntity(p));
    }

    /** The profile id a device token belongs to. */
    public Optional<Long> profileFor(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            return Optional.empty();
        }
        return repository.findByAuthToken(token).map(LearnerProfile::getId);
    }

    static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw bad("הסיסמה צריכה להיות לפחות " + MIN_PASSWORD + " תווים");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String newToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    String hash(String password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(pbkdf2(password, salt, ITERATIONS));
    }

    static boolean matches(String password, String stored) {
        if (stored == null) {
            return false; // a profile from before accounts - no password to match
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        return MessageDigest.isEqual(expected, pbkdf2(password, salt, Integer.parseInt(parts[1])));
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
