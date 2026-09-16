package uk.gov.hmcts.cp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public final class TestTokens {

    public static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    public static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    public static final String CLIENT_ID = "33333333-3333-3333-3333-333333333333";
    public static final String OBJECT_ID = "44444444-4444-4444-4444-444444444444";
    public static final String SIBLING_API_AUDIENCE = "55555555-5555-5555-5555-555555555555";
    public static final String GRAPH_AUDIENCE = "00000003-0000-0000-c000-000000000000";
    public static final String ROLE = "app.read";
    public static final String ISSUER = "https://login.microsoftonline.com/" + TENANT_ID + "/v2.0";
    public static final String SIGNING_KEY_ID = "test-signing-key";

    public static final RSAKey SIGNING_KEY = generateKey(SIGNING_KEY_ID);
    public static final RSAKey UNRELATED_KEY = generateKey("unrelated-key");

    private TestTokens() {
    }

    public static JWTClaimsSet.Builder appOnlyClaims() {
        final Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
            .audience(AUDIENCE)
            .issuer(ISSUER)
            .subject(OBJECT_ID)
            .claim("oid", OBJECT_ID)
            .claim("azp", CLIENT_ID)
            .claim("tid", TENANT_ID)
            .claim("ver", "2.0")
            .claim("roles", List.of(ROLE))
            .issueTime(Date.from(now))
            .notBeforeTime(Date.from(now))
            .expirationTime(Date.from(now.plus(Duration.ofHours(1))));
    }

    public static String bearer(final JWTClaimsSet claims) {
        return "Bearer " + sign(SIGNING_KEY, claims);
    }

    public static String validBearer() {
        return bearer(appOnlyClaims().build());
    }

    public static String sign(final RSAKey key, final JWTClaimsSet claims) {
        return sign(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), key, claims);
    }

    public static String sign(final JWSHeader header, final RSAKey key, final JWTClaimsSet claims) {
        try {
            final SignedJWT jwt = new SignedJWT(header, claims);
            final JWSSigner signer = new RSASSASigner(key);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign test token", e);
        }
    }

    private static RSAKey generateKey(final String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("could not generate test key", e);
        }
    }
}
