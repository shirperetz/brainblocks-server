package com.example.brainblocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.brainblocks.learning.model.LearnRecoveryCode;
import com.example.brainblocks.learning.repository.LearnRecoveryCodeRepository;
import com.example.brainblocks.learning.repository.LearnerProfileRepository;
import com.example.brainblocks.learning.service.AdminSecondFactor;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * 2026-11-14 (Shir: "another password or my fingerprint"; "the code in case I change phones"): on the
 * public site the admin's password alone gives no token; a passkey (simulated here with a real P-256 key,
 * exactly as a phone signs) or a one-time backup code does. Children are not affected.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DATABASE_URL=jdbc:h2:mem:second;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "DATABASE_USER=sa", "DATABASE_PASSWORD=",
        "JOIN_CODE=4711", "ADMIN_USERNAMES=boss", "PUBLIC_ORIGIN=https://example.org", "ADMIN_2FA=on",
        "server.port=0"})
@ActiveProfiles("cloud")
class AdminSecondFactorTest {

    static final String ORIGIN = "https://example.org";

    @Autowired TestRestTemplate http;
    @Autowired LearnRecoveryCodeRepository codes;
    @Autowired LearnerProfileRepository profiles;

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] sha(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    static byte[] clientData(String type, String challenge, String origin) {
        return ("{\"type\":\"" + type + "\",\"challenge\":\"" + challenge + "\",\"origin\":\"" + origin + "\",\"crossOrigin\":false}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Object body, String token, int expect) {
        HttpHeaders h = new HttpHeaders();
        if (token != null) h.set("X-Learner-Token", token);
        ResponseEntity<Map> r = http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), Map.class);
        assertThat(r.getStatusCode().value()).as(path).isEqualTo(expect);
        return r.getBody();
    }

    private Map<String, Object> login() {
        return post("/api/learn/auth/login", Map.of("username", "boss", "password", "secret1234"), null, 200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theAdminNeedsHerFingerprintOrACode() throws Exception {
        // signing up as the admin name gives no token either (a stranger on an empty site gets nothing)
        Map<String, Object> reg = post("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711", "username", "boss",
                "password", "secret1234", "name", "שיר", "avatar", "GIRL", "grade", "G6")), null, 201);
        assertThat(reg).containsEntry("secondStep", true).doesNotContainKey("token");

        Map<String, Object> t = login();
        assertThat(t).containsEntry("secondStep", true).containsEntry("rpId", "example.org").doesNotContainKey("token");
        assertThat(t.toString()).doesNotContain("secret1234");
        post("/api/learn/auth/second/code", Map.of("ticket", t.get("ticket"), "code", "AAAA-BBBB-CCCC"), null, 401);

        // a backup code works once (dashes / case don't matter)
        Long bossId = profiles.findByUsername("boss").orElseThrow().getId();
        String code = AdminSecondFactor.newCode();
        codes.save(new LearnRecoveryCode(bossId, AdminSecondFactor.hash(code)));
        Map<String, Object> s = post("/api/learn/auth/second/code", Map.of("ticket", t.get("ticket"),
                "code", code.toLowerCase().replace("-", " ")), null, 200);
        String token = (String) s.get("token");
        assertThat(token).isNotBlank();
        Map<String, Object> t2 = login();
        post("/api/learn/auth/second/code", Map.of("ticket", t2.get("ticket"), "code", code), null, 401);
        // the used ticket is gone
        post("/api/learn/auth/second/code", Map.of("ticket", t.get("ticket"), "code", code), null, 401);

        // new codes: needs the password again; 8 codes; the old ones stop working
        post("/api/learn/admin/security/codes", Map.of("password", "wrong-password"), token, 403);
        List<String> fresh = (List<String>) post("/api/learn/admin/security/codes", Map.of("password", "secret1234"), token, 200).get("codes");
        assertThat(fresh).hasSize(8).allMatch(c -> c.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}"));

        // register a passkey, as a phone does
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair key = g.generateKeyPair();
        byte[] credId = sha("my-phone".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> opt = post("/api/learn/admin/security/passkey-options", Map.of(), token, 200);
        ByteArrayOutputStream ad = new ByteArrayOutputStream();
        ad.write(sha("example.org".getBytes(StandardCharsets.UTF_8)));
        ad.write(0x45); // user present + verified + credential data
        ad.write(new byte[4]);
        ad.write(new byte[16]);
        ad.write(new byte[]{0, (byte) credId.length});
        ad.write(credId);
        ad.write(new byte[]{(byte) 0xa5, 1, 2}); // (the COSE key - not read, the browser hands over the SPKI)
        Map<String, Object> reg2 = new HashMap<>();
        reg2.put("id", b64(credId));
        reg2.put("publicKey", b64(key.getPublic().getEncoded()));
        reg2.put("alg", -7);
        reg2.put("authData", b64(ad.toByteArray()));
        reg2.put("clientData", b64(clientData("webauthn.create", (String) opt.get("challenge"), ORIGIN)));
        reg2.put("name", "הטלפון שלי");
        Map<String, Object> state = post("/api/learn/admin/security/passkeys", reg2, token, 200);
        assertThat((List<Object>) state.get("passkeys")).hasSize(1);

        // log in with it
        Map<String, Object> t3 = login();
        assertThat((List<String>) t3.get("passkeys")).containsExactly(b64(credId));
        String challenge = (String) t3.get("challenge");
        Map<String, Object> ok = post("/api/learn/auth/second/passkey",
                assertion(key, credId, t3.get("ticket"), challenge, ORIGIN, 0x05), null, 200);
        assertThat(ok.get("token")).isEqualTo(token);

        // another site's page, no fingerprint (only "present"), or a different key: refused
        Map<String, Object> t4 = login();
        post("/api/learn/auth/second/passkey", assertion(key, credId, t4.get("ticket"), (String) t4.get("challenge"),
                "https://evil.example", 0x05), null, 401);
        post("/api/learn/auth/second/passkey", assertion(key, credId, t4.get("ticket"), (String) t4.get("challenge"),
                ORIGIN, 0x01), null, 401);
        KeyPair other = g.generateKeyPair();
        post("/api/learn/auth/second/passkey", assertion(other, credId, t4.get("ticket"), (String) t4.get("challenge"),
                ORIGIN, 0x05), null, 401);

        // a child signs up and logs in exactly as before (the admin approves it)
        Map<String, Object> kid = post("/api/learn/auth/register", new HashMap<>(Map.of("joinCode", "4711", "username", "kidsecond",
                "password", "secret12", "name", "kid", "avatar", "GIRL", "grade", "G3")), null, 201);
        Long kidId = ((Number) ((Map<String, Object>) kid.get("profile")).get("id")).longValue();
        post("/api/learn/staff/children/" + kidId + "/approve", null, token, 200);
        Map<String, Object> kidLogin = post("/api/learn/auth/login", Map.of("username", "kidsecond", "password", "secret12"), null, 200);
        assertThat(kidLogin.get("token")).isNotNull();
        assertThat(kidLogin).doesNotContainKey("secondStep");

        // no voice in the cloud: the words come back for the browser's own voice
        HttpHeaders h = new HttpHeaders();
        h.set("X-Learner-Token", (String) kidLogin.get("token"));
        ResponseEntity<byte[]> sp = http.exchange("/api/learn/speak", HttpMethod.POST, new HttpEntity<>(Map.of("text", "כמה זה 38 + 27?"), h), byte[].class);
        assertThat(sp.getStatusCode().value()).isEqualTo(204);
        String words = URLDecoder.decode(sp.getHeaders().getFirst("X-Spoken"), StandardCharsets.UTF_8);
        assertThat(words).contains("שלושים ושמונה").contains("עשרים ושבע").doesNotContain("38");

        // the waker's address answers without the database
        assertThat(http.getForObject("/api/ping", String.class)).isEqualTo("ok");
    }

    private static Map<String, Object> assertion(KeyPair key, byte[] credId, Object ticket, String challenge, String origin, int flags)
            throws Exception {
        ByteArrayOutputStream ad = new ByteArrayOutputStream();
        ad.write(sha("example.org".getBytes(StandardCharsets.UTF_8)));
        ad.write(flags);
        ad.write(ByteBuffer.allocate(4).putInt(0).array());
        byte[] authData = ad.toByteArray();
        byte[] cd = clientData("webauthn.get", challenge, origin);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key.getPrivate());
        s.update(authData);
        s.update(sha(cd));
        Map<String, Object> m = new HashMap<>();
        m.put("ticket", ticket);
        m.put("id", b64(credId));
        m.put("authData", b64(authData));
        m.put("clientData", b64(cd));
        m.put("signature", b64(s.sign()));
        return m;
    }
}
