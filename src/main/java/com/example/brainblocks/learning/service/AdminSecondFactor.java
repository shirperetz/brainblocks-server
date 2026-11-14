package com.example.brainblocks.learning.service;

import com.example.brainblocks.learning.model.LearnAdminKey;
import com.example.brainblocks.learning.model.LearnRecoveryCode;
import com.example.brainblocks.learning.repository.LearnAdminKeyRepository;
import com.example.brainblocks.learning.repository.LearnRecoveryCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2026-11-14 (Shir, for the public site: "another password or my fingerprint"; "give me the code in case
 * I change phones"): the admin's second step. After the right password the admin gets no token yet,
 * only a 5-minute ticket; the token comes after her fingerprint / face / PIN (a passkey on her device)
 * or one of her one-time backup codes. Children and teachers are not affected.
 * On only when brainblocks.admin-2fa=on (the cloud); on this PC the admin is already locked to her own
 * devices (admin-network).
 */
@Service
public class AdminSecondFactor {

    private static final Logger log = LoggerFactory.getLogger("brainblocks.admin");
    static final long TICKET_SECONDS = 300;
    static final int MAX_TRIES = 5;
    public static final int CODES = 8;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O, 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean on;
    private final String origin;
    private final String rpId;
    private final LearnAdminKeyRepository keys;
    private final LearnRecoveryCodeRepository codes;
    private final AccountService accounts;

    @Autowired(required = false)
    private PhoneAlerts alerts;

    private record Ticket(AccountService.Session session, byte[] challenge, Instant until, int[] tries) {}

    private record Challenge(byte[] bytes, Instant until) {}

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<Long, Challenge> registering = new ConcurrentHashMap<>();

    public AdminSecondFactor(@Value("${brainblocks.admin-2fa:off}") String mode,
                             @Value("${brainblocks.public-origin:}") String origin,
                             LearnAdminKeyRepository keys, LearnRecoveryCodeRepository codes, AccountService accounts) {
        this.on = "on".equals(mode);
        this.origin = origin == null ? "" : origin.replaceAll("/+$", "");
        String host = "";
        try {
            host = this.origin.isEmpty() ? "" : URI.create(this.origin).getHost();
        } catch (RuntimeException ignored) {
            // a bad address - passkeys stay off, backup codes still work
        }
        this.rpId = host == null ? "" : host;
        this.keys = keys;
        this.codes = codes;
        this.accounts = accounts;
        if (on && rpId.isEmpty()) log.warn("admin-2fa is on but brainblocks.public-origin is empty: only backup codes work");
    }

    public boolean required(AccountService.Session s) {
        return on && s != null && s.profile() != null && s.profile().isAdmin();
    }

    private static byte[] random(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private void sweep(Instant now) {
        tickets.values().removeIf(t -> now.isAfter(t.until()));
        registering.values().removeIf(c -> now.isAfter(c.until()));
    }

    /** Right password, admin: a ticket instead of the token, and what the second step can use. */
    public Map<String, Object> begin(AccountService.Session s) {
        Instant now = Instant.now();
        sweep(now);
        String ticket = WebAuthn.b64url(random(24));
        byte[] challenge = random(32);
        tickets.put(ticket, new Ticket(s, challenge, now.plusSeconds(TICKET_SECONDS), new int[]{0}));
        Long id = s.profile().getId();
        List<String> ids = keys.findByProfileIdOrderByIdAsc(id).stream().map(LearnAdminKey::getCredentialId).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("secondStep", true);
        out.put("ticket", ticket);
        out.put("passkeys", rpId.isEmpty() ? List.of() : ids);
        out.put("challenge", WebAuthn.b64url(challenge));
        out.put("rpId", rpId);
        out.put("codesLeft", codes.findByProfileIdAndUsedAtIsNull(id).size());
        return out;
    }

    private Ticket ticket(String ticket) {
        Ticket t = ticket == null ? null : tickets.get(ticket);
        if (t == null || Instant.now().isAfter(t.until())) {
            if (ticket != null) tickets.remove(ticket);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "עבר יותר מדי זמן. נכנסים שוב עם הסיסמה");
        }
        if (++t.tries()[0] > MAX_TRIES) {
            tickets.remove(ticket);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "יותר מדי ניסיונות. נכנסים שוב עם הסיסמה");
        }
        return t;
    }

    /** Second step with a passkey (fingerprint / face / PIN on her device). */
    public AccountService.Session withPasskey(String ticket, String credentialId, String clientData, String authData, String signature) {
        Ticket t = ticket(ticket);
        LearnAdminKey k = credentialId == null ? null : keys.findByCredentialId(credentialId).orElse(null);
        if (k == null || !k.getProfileId().equals(t.session().profile().getId()) || rpId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "טביעת האצבע הזו לא רשומה. אפשר להיכנס עם קוד גיבוי");
        }
        try {
            long count = WebAuthn.verifyAssertion(WebAuthn.b64url(clientData), WebAuthn.b64url(authData), WebAuthn.b64url(signature),
                    java.util.Base64.getDecoder().decode(k.getPublicKey()), k.getAlg(), k.getSignCount(), t.challenge(), origin, rpId);
            k.setSignCount(count);
            k.setLastUsedAt(LocalDateTime.now());
            keys.save(k);
        } catch (WebAuthn.Invalid | RuntimeException e) {
            log.warn("admin passkey refused: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "האימות לא הצליח. נסי שוב");
        }
        tickets.remove(ticket);
        log.info("admin logged in with a passkey ({})", k.getName());
        return t.session();
    }

    /** Second step with a one-time backup code (a new phone, or no fingerprint at hand). */
    public AccountService.Session withCode(String ticket, String code) {
        Ticket t = ticket(ticket);
        Long id = t.session().profile().getId();
        byte[] h = hash(code).getBytes(StandardCharsets.US_ASCII);
        for (LearnRecoveryCode c : codes.findByProfileIdAndUsedAtIsNull(id)) {
            if (MessageDigest.isEqual(c.getCodeHash().getBytes(StandardCharsets.US_ASCII), h)) {
                c.setUsedAt(LocalDateTime.now());
                codes.save(c);
                tickets.remove(ticket);
                int left = codes.findByProfileIdAndUsedAtIsNull(id).size();
                log.warn("admin logged in with a backup code ({} left)", left);
                if (alerts != null) alerts.send("admin-code", "נכנסו לחשבון המנהלת עם קוד גיבוי. נשארו " + left + " קודים");
                return t.session();
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "הקוד לא נכון (או שכבר השתמשו בו)");
    }

    /** "ABCD-EFGH-JKLM": case, spaces and dashes don't matter. */
    public static String hash(String code) {
        String n = code == null ? "" : code.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return HexFormat.of().formatHex(WebAuthn.sha256(("bb-recovery:" + n).getBytes(StandardCharsets.UTF_8)));
    }

    public static String newCode() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) b.append('-');
            b.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return b.toString();
    }

    // ---- managing (the admin, logged in) ------------------------------------------------

    public Map<String, Object> state(Long adminId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("on", on);
        out.put("rpId", rpId);
        out.put("codesLeft", codes.findByProfileIdAndUsedAtIsNull(adminId).size());
        List<Map<String, Object>> list = new ArrayList<>();
        for (LearnAdminKey k : keys.findByProfileIdOrderByIdAsc(adminId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", k.getId());
            m.put("name", k.getName());
            m.put("createdAt", k.getCreatedAt());
            m.put("lastUsedAt", k.getLastUsedAt());
            list.add(m);
        }
        out.put("passkeys", list);
        return out;
    }

    public Map<String, Object> registrationOptions(Long adminId) {
        if (rpId.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "טביעת אצבע עובדת רק באתר הציבורי");
        Instant now = Instant.now();
        sweep(now);
        byte[] ch = random(32);
        registering.put(adminId, new Challenge(ch, now.plusSeconds(TICKET_SECONDS)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("challenge", WebAuthn.b64url(ch));
        out.put("rpId", rpId);
        out.put("userId", WebAuthn.b64url(("bb-" + adminId).getBytes(StandardCharsets.UTF_8)));
        out.put("exclude", keys.findByProfileIdOrderByIdAsc(adminId).stream().map(LearnAdminKey::getCredentialId).toList());
        return out;
    }

    public Map<String, Object> register(Long adminId, Map<String, Object> body) {
        Challenge c = registering.remove(adminId);
        if (c == null || Instant.now().isAfter(c.until())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "עבר יותר מדי זמן. נסי שוב");
        }
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        if (name.isEmpty() || "null".equals(name)) name = "מכשיר";
        if (name.length() > 40) name = name.substring(0, 40);
        try {
            byte[] credId = WebAuthn.b64url(String.valueOf(body.get("id")));
            byte[] spki = WebAuthn.b64url(String.valueOf(body.get("publicKey")));
            int alg = ((Number) body.get("alg")).intValue();
            long count = WebAuthn.verifyRegistration(WebAuthn.b64url(String.valueOf(body.get("clientData"))),
                    WebAuthn.b64url(String.valueOf(body.get("authData"))), credId, spki, alg, c.bytes(), origin, rpId);
            String idText = WebAuthn.b64url(credId);
            if (keys.findByCredentialId(idText).isPresent()) throw new WebAuthn.Invalid("already saved");
            keys.save(new LearnAdminKey(adminId, idText, java.util.Base64.getEncoder().encodeToString(spki), alg, count, name));
        } catch (WebAuthn.Invalid | RuntimeException e) {
            log.warn("admin passkey registration refused: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "לא הצלחנו לשמור את טביעת האצבע. נסי שוב");
        }
        if (alerts != null) alerts.send("admin-key", "נוספה טביעת אצבע לחשבון המנהלת: " + name);
        return state(adminId);
    }

    public Map<String, Object> removeKey(Long adminId, Long keyId) {
        keys.findById(keyId).filter(k -> k.getProfileId().equals(adminId)).ifPresent(keys::delete);
        return state(adminId);
    }

    /** New backup codes (the old ones stop working); shown once. Needs her password again. */
    public List<String> newCodes(Long adminId, String password) {
        if (password == null || !accounts.passwordOk(adminId, password)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "הסיסמה לא נכונה");
        }
        codes.deleteAll(codes.findByProfileId(adminId));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < CODES; i++) {
            String code = newCode();
            codes.save(new LearnRecoveryCode(adminId, hash(code)));
            out.add(code);
        }
        return out;
    }
}
