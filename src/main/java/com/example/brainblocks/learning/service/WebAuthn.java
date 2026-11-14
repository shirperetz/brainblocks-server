package com.example.brainblocks.learning.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 2026-11-14 (Shir: fingerprint for the admin): the server side of a passkey (WebAuthn), kept small on
 * purpose. The browser hands over the public key itself (getPublicKey(), SubjectPublicKeyInfo), so no
 * attestation/CBOR parsing is needed ("none" attestation - we trust the device Shir registers while she
 * is logged in). What is checked, every time: the type, our challenge, our exact origin, the hash of our
 * site name (rpId), that the user was present AND verified (fingerprint / face / PIN), and the signature.
 */
public final class WebAuthn {

    private WebAuthn() {}

    public static final int ES256 = -7;
    public static final int RS256 = -257;
    private static final ObjectMapper JSON = new ObjectMapper();

    public static final class Invalid extends Exception {
        public Invalid(String why) {
            super(why);
        }
    }

    public static byte[] b64url(String s) throws Invalid {
        try {
            return Base64.getUrlDecoder().decode(s.replace('+', '-').replace('/', '_').replace("=", ""));
        } catch (RuntimeException e) {
            throw new Invalid("bad base64");
        }
    }

    public static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** clientDataJSON: right type, our challenge, our origin. */
    static void checkClientData(byte[] clientDataJson, String type, byte[] challenge, String origin) throws Invalid {
        JsonNode c;
        try {
            c = JSON.readTree(new String(clientDataJson, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new Invalid("client data is not JSON");
        }
        if (!type.equals(c.path("type").asText())) throw new Invalid("wrong type");
        byte[] got = b64url(c.path("challenge").asText(""));
        if (!MessageDigest.isEqual(got, challenge)) throw new Invalid("wrong challenge");
        if (!origin.equals(c.path("origin").asText())) throw new Invalid("wrong origin: " + c.path("origin").asText());
        if (c.path("crossOrigin").asBoolean(false)) throw new Invalid("cross-origin");
    }

    /** authenticatorData: our rpId hash, user present + verified. Returns the sign counter. */
    static long checkAuthData(byte[] authData, String rpId) throws Invalid {
        if (authData == null || authData.length < 37) throw new Invalid("short authenticator data");
        if (!MessageDigest.isEqual(Arrays.copyOfRange(authData, 0, 32), sha256(rpId.getBytes(StandardCharsets.UTF_8)))) {
            throw new Invalid("wrong site (rpId)");
        }
        int flags = authData[32] & 0xff;
        if ((flags & 0x01) == 0) throw new Invalid("user not present");
        if ((flags & 0x04) == 0) throw new Invalid("user not verified (no fingerprint / PIN)");
        return ByteBuffer.wrap(authData, 33, 4).getInt() & 0xffffffffL;
    }

    /** The credential id inside a registration's authenticatorData (attested credential data). */
    static byte[] credentialIdIn(byte[] authData) throws Invalid {
        if ((authData[32] & 0x40) == 0 || authData.length < 55) throw new Invalid("no credential in authenticator data");
        int len = ((authData[53] & 0xff) << 8) | (authData[54] & 0xff);
        if (55 + len > authData.length) throw new Invalid("bad credential length");
        return Arrays.copyOfRange(authData, 55, 55 + len);
    }

    public static PublicKey publicKey(byte[] spki, int alg) throws Invalid {
        try {
            PublicKey k;
            if (alg == ES256) {
                k = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(spki));
                if (!(k instanceof ECPublicKey ec) || ec.getParams().getCurve().getField().getFieldSize() != 256) {
                    throw new Invalid("not a P-256 key");
                }
            } else if (alg == RS256) {
                k = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
                if (!(k instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 2048) throw new Invalid("weak RSA key");
            } else {
                throw new Invalid("unsupported algorithm " + alg);
            }
            return k;
        } catch (Invalid e) {
            throw e;
        } catch (Exception e) {
            throw new Invalid("bad public key");
        }
    }

    /** A new passkey: returns its sign counter. */
    public static long verifyRegistration(byte[] clientDataJson, byte[] authData, byte[] credentialId, byte[] spki, int alg,
                                          byte[] challenge, String origin, String rpId) throws Invalid {
        checkClientData(clientDataJson, "webauthn.create", challenge, origin);
        long count = checkAuthData(authData, rpId);
        if (!MessageDigest.isEqual(credentialIdIn(authData), credentialId)) throw new Invalid("credential id mismatch");
        publicKey(spki, alg);
        return count;
    }

    /** A log-in with a saved passkey: returns the new sign counter. */
    public static long verifyAssertion(byte[] clientDataJson, byte[] authData, byte[] signature, byte[] spki, int alg,
                                       long storedCount, byte[] challenge, String origin, String rpId) throws Invalid {
        checkClientData(clientDataJson, "webauthn.get", challenge, origin);
        long count = checkAuthData(authData, rpId);
        try {
            Signature s = Signature.getInstance(alg == ES256 ? "SHA256withECDSA" : "SHA256withRSA");
            s.initVerify(publicKey(spki, alg));
            s.update(authData);
            s.update(sha256(clientDataJson));
            if (!s.verify(signature)) throw new Invalid("bad signature");
        } catch (Invalid e) {
            throw e;
        } catch (Exception e) {
            throw new Invalid("bad signature");
        }
        // A counter that goes backwards = a copied key (most passkeys always send 0 - that's fine).
        if (count != 0 && storedCount != 0 && count <= storedCount) throw new Invalid("sign counter went back");
        return count;
    }
}
