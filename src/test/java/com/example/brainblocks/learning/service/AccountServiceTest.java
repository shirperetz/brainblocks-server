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
    void aChildChangesTheirOwnPasswordOnlyWithTheCurrentOne() {
        List<LearnerProfile> db = new ArrayList<>();
        AccountService a = service(db);
        var made = a.register("tal", "1234", "טל", "ROBOT", "G2", null);
        a.approve(made.profile().getId());
        String oldToken = a.login("tal", "1234", Instant.now()).token();
        assertThatThrownBy(() -> a.changeOwnPassword(made.profile().getId(), "9999", "abcd"))
                .hasMessageContaining("הנוכחית");
        assertThatThrownBy(() -> a.changeOwnPassword(made.profile().getId(), "1234", "ab"))
                .hasMessageContaining("לפחות");
        var changed = a.changeOwnPassword(made.profile().getId(), "1234", "abcd");
        assertThat(changed.token()).isNotNull().isNotEqualTo(oldToken);
        assertThat(a.profileFor(oldToken)).isEmpty(); // other devices log in again
        assertThat(a.login("tal", "abcd", Instant.now()).profile().getId()).isEqualTo(made.profile().getId());
        assertThatThrownBy(() -> a.login("tal", "1234", Instant.now())).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void halfAnHourWithoutUseMeansLoggingInAgain() {
        List<LearnerProfile> db = new ArrayList<>();
        AccountService a = service(db);
        var made = a.register("ido", "1234", "עידו", "ROBOT", "G2", null);
        a.approve(made.profile().getId());
        Instant t0 = Instant.parse("2026-11-11T10:00:00Z");
        String token = a.login("ido", "1234", t0).token();
        java.time.LocalDateTime l0 = java.time.LocalDateTime.ofInstant(t0, java.time.ZoneId.systemDefault());
        // used every 20 minutes - stays in
        assertThat(a.profileFor(token, l0.plusMinutes(20))).isPresent();
        assertThat(a.profileFor(token, l0.plusMinutes(45))).isPresent();
        // then 31 minutes of nothing - out, and the token is gone for good
        assertThat(a.profileFor(token, l0.plusMinutes(77))).isEmpty();
        assertThat(a.profileFor(token, l0.plusMinutes(78))).isEmpty();
        // logging in again gives a NEW token
        String again = a.login("ido", "1234", t0.plusSeconds(80 * 60)).token();
        assertThat(again).isNotEqualTo(token);
        assertThat(a.profileFor(again, l0.plusMinutes(81))).isPresent();
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
        // 2026-11-11: a new sign-up waits for the admin - no token, no log-in until approved
        assertThat(made.token()).isNull();
        assertThat(made.profile().isApproved()).isFalse();
        assertThat(made.profile().getUsername()).isEqualTo("noa");
        assertThatThrownBy(() -> a.login("noa", "1234", Instant.now())).hasMessageContaining("מחכה לאישור");
        a.approve(made.profile().getId());
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
    void signUpNeedsTheJoinCode() {
        List<LearnerProfile> db = new ArrayList<>();
        var a = withCode(db, "482915");
        assertThatThrownBy(() -> a.register(null, "noa", "1234", "נועה", "ROBOT", null, null)).hasMessageContaining("קוד ההצטרפות");
        assertThatThrownBy(() -> a.register("000000", "noa", "1234", "נועה", "ROBOT", null, null)).hasMessageContaining("קוד ההצטרפות");
        assertThat(a.register(" 482915 ", "noa", "1234", "נועה", "ROBOT", null, null).profile().getUsername()).isEqualTo("noa");
        var closed = withCode(new ArrayList<>(), "");
        assertThatThrownBy(() -> closed.register("", "dan", "1234", "דן", "ROBOT", null, null)).hasMessageContaining("קוד ההצטרפות");
    }

    private static AccountService withCode(List<LearnerProfile> db, String code) {
        AccountService base = service(db);
        try {
            var f = AccountService.class.getDeclaredField("repository");
            f.setAccessible(true);
            var repo = (LearnerProfileRepository) f.get(base);
            return new AccountService(repo, new LearnerProfileService(repo), code);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void fiveWrongPasswordsPauseTheUsername() {
        AccountService a = service(new ArrayList<>());
        a.approve(a.register("dan", "secret", "דן", "ROBOT", null, null).profile().getId());
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
                    case "findAll" -> new ArrayList<>(db);
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

    @Test
    void adminResetsPasswordsBlocksAndChangesTheCode() {
        List<LearnerProfile> db = new ArrayList<>();
        AccountService a = service(db);
        var kid = a.register("noa", "1234", "נועה", "ROBOT", null, null);
        Long id = kid.profile().getId();
        a.approve(id);
        var first = a.login("noa", "1234", Instant.now());
        assertThat(a.profileFor(first.token())).contains(id);
        // a forgotten password: new one works, the old one and old devices don't
        a.setCredentials(id, null, "5678");
        assertThatThrownBy(() -> a.login("noa", "1234", Instant.now())).hasMessageContaining("לא נכונים");
        assertThat(a.profileFor(first.token())).isEmpty();
        var again = a.login("noa", "5678", Instant.now());
        // blocked: no log-in, device token stops; unblocked: works again
        a.setDisabled(id, true, 999L);
        assertThat(a.profileFor(again.token())).isEmpty();
        assertThatThrownBy(() -> a.login("noa", "5678", Instant.now())).hasMessageContaining("חסום");
        a.setDisabled(id, false, 999L);
        assertThat(a.login("noa", "5678", Instant.now()).token()).isNotBlank();
        assertThatThrownBy(() -> a.setDisabled(999L, true, 999L)).hasMessageContaining("את עצמך");
        // join code
        assertThat(a.changeJoinCode(" 7777 ")).isEqualTo("7777");
        assertThat(a.register("7777", "dan", "1234", "דן", "ROBOT", null, null).profile().isApproved()).isFalse();
        assertThatThrownBy(() -> a.changeJoinCode("1 2")).hasMessageContaining("4 עד 20");
        assertThat(a.changeJoinCode("")).isEmpty(); // sign-up closed
        assertThatThrownBy(() -> a.register("", "eli", "1234", "אלי", "ROBOT", null, null)).hasMessageContaining("קוד ההצטרפות");
        assertThat(a.isAdmin(id)).isFalse(); // only the database makes an admin
    }

    /** 2026-11-13: staff see many children - at least 10 characters (a child: 4). */
    @org.junit.jupiter.api.Test
    void staffPasswordsAreLonger() {
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> AccountService.checkStaffPassword("secret12"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> AccountService.checkStaffPassword(null));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> AccountService.checkStaffPassword("secret1234"));
    }
}
