package com.example.brainblocks.learning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.brainblocks.learning.model.Avatar;
import com.example.brainblocks.learning.model.LearnerProfile;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** 2026-11-11 children's accounts: hashing, usernames, log-in, lockout - against an in-memory repository. */
class AccountServiceTest {

    @Test
    void passwordIsHashedWithSaltAndChecked() {
        AccountService a = service(new ArrayList<>());
        String h1 = a.hash("1234");
        String h2 = a.hash("1234");
        assertThat(h1).startsWith("pbkdf2$").doesNotContain("1234").isNotEqualTo(h2); // salted
        assertThat(AccountService.matches("1234", h1)).isTrue();
        assertThat(AccountService.matches("1235", h1)).isFalse();
        assertThat(AccountService.matches("1234", null)).isFalse(); // an old profile without a password
    }

    @Test
    void usernames() {
        assertThat(AccountService.USERNAME.matcher(AccountService.normalize(" Noa_7 ")).matches()).isTrue();
        assertThat(AccountService.USERNAME.matcher("נועה2").matches()).isTrue();
        assertThat(AccountService.USERNAME.matcher("ab").matches()).isFalse();
        assertThat(AccountService.USERNAME.matcher("noa cohen").matches()).isFalse();
    }

    @Test
    void registerThenLoginGivesTheSameChildOnAnotherDevice() {
        List<LearnerProfile> db = new ArrayList<>();
        AccountService a = service(db);
        var made = a.register("Noa", "1234", "נועה", "ROBOT", "G3", null);
        assertThat(made.token()).hasSizeGreaterThan(30);
        assertThat(made.profile().getUsername()).isEqualTo("noa");
        var again = a.login("NOA", "1234", Instant.now());
        assertThat(again.profile().getId()).isEqualTo(made.profile().getId());
        assertThat(a.profileFor(again.token())).contains(made.profile().getId());
        assertThat(a.profileFor("nope")).isEmpty();
        assertThatThrownBy(() -> a.register("noa", "9999", "אחרת", "ROBOT", null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("תפוס");
        assertThatThrownBy(() -> a.register("dan", "12", "דן", "ROBOT", null, null))
                .hasMessageContaining("לפחות");
    }

    @Test
    void fiveWrongPasswordsPauseTheUsername() {
        AccountService a = service(new ArrayList<>());
        a.register("dan", "secret", "דן", "ROBOT", null, null);
        Instant t = Instant.parse("2026-11-11T16:00:00Z");
        for (int i = 0; i < AccountService.MAX_FAILURES; i++) {
            assertThatThrownBy(() -> a.login("dan", "wrong", t)).hasMessageContaining("לא נכונים");
        }
        assertThatThrownBy(() -> a.login("dan", "secret", t)).hasMessageContaining("יותר מדי");
        assertThat(a.login("dan", "secret", t.plus(AccountService.LOCK).plusSeconds(1)).token()).isNotBlank();
    }

    /** A tiny in-memory LearnerProfileRepository (only what the services call). */
    private static AccountService service(List<LearnerProfile> db) {
        LearnerProfileRepository repo = (LearnerProfileRepository) Proxy.newProxyInstance(
                AccountServiceTest.class.getClassLoader(), new Class<?>[] {LearnerProfileRepository.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "save" -> {
                        LearnerProfile p = (LearnerProfile) args[0];
                        if (p.getId() == null) {
                            var f = LearnerProfile.class.getDeclaredField("id");
                            f.setAccessible(true);
                            f.set(p, (long) db.size() + 1);
                            db.add(p);
                        }
                        yield p;
                    }
                    case "findById" -> db.stream().filter(p -> p.getId().equals(args[0])).findFirst();
                    case "findByUsername" -> db.stream().filter(p -> args[0].equals(p.getUsername())).findFirst();
                    case "findByAuthToken" -> db.stream().filter(p -> args[0].equals(p.getAuthToken())).findFirst();
                    case "existsByUsername" -> db.stream().anyMatch(p -> args[0].equals(p.getUsername()));
                    case "toString" -> "repo";
                    case "hashCode" -> 1;
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
        return new AccountService(repo, new LearnerProfileService(repo));
    }

    @SuppressWarnings("unused")
    private static Optional<Avatar> unused() {
        return Optional.empty();
    }
}
