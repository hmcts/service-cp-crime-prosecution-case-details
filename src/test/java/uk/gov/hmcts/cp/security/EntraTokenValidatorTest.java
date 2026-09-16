package uk.gov.hmcts.cp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.hmcts.cp.config.AuthProperties;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.cp.security.TestTokens.AUDIENCE;
import static uk.gov.hmcts.cp.security.TestTokens.CLIENT_ID;
import static uk.gov.hmcts.cp.security.TestTokens.GRAPH_AUDIENCE;
import static uk.gov.hmcts.cp.security.TestTokens.ISSUER;
import static uk.gov.hmcts.cp.security.TestTokens.OBJECT_ID;
import static uk.gov.hmcts.cp.security.TestTokens.ROLE;
import static uk.gov.hmcts.cp.security.TestTokens.SIBLING_API_AUDIENCE;
import static uk.gov.hmcts.cp.security.TestTokens.SIGNING_KEY;
import static uk.gov.hmcts.cp.security.TestTokens.SIGNING_KEY_ID;
import static uk.gov.hmcts.cp.security.TestTokens.TENANT_ID;
import static uk.gov.hmcts.cp.security.TestTokens.UNRELATED_KEY;
import static uk.gov.hmcts.cp.security.TestTokens.appOnlyClaims;
import static uk.gov.hmcts.cp.security.TestTokens.bearer;
import static uk.gov.hmcts.cp.security.TestTokens.sign;
import static uk.gov.hmcts.cp.security.TestTokens.validBearer;

class EntraTokenValidatorTest {

    private static final long CLOCK_SKEW_SECONDS = 60L;

    private EntraTokenValidator validator;

    @BeforeEach
    void setUp() {
        final JWKSource<SecurityContext> jwkSource =
            new ImmutableJWKSet<>(new JWKSet(SIGNING_KEY.toPublicJWK()));
        validator = new EntraTokenValidator(jwkSource, authProperties());
    }

    private static AuthProperties authProperties() {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        return new AuthProperties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, "", "",
            CLOCK_SKEW_SECONDS, 3600L, ROLE, environment);
    }

    private void assertRejected(final String authorizationHeader, final TokenRejectionReason reason) {
        assertThatThrownBy(() -> validator.validate(authorizationHeader))
            .isInstanceOf(TokenValidationException.class)
            .extracting(e -> ((TokenValidationException) e).getReason())
            .isEqualTo(reason);
    }

    // --- Acceptance ---

    @Test
    @DisplayName("A well-formed app-only token is accepted and yields the caller's client id")
    void accepts_well_formed_app_only_token() throws Exception {
        final CallerIdentity caller = validator.validate(validBearer());

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
        assertThat(caller.verified()).isTrue();
    }

    @Test
    @DisplayName("A token without idtyp is accepted - app-only must never be made to require it")
    void accepts_token_without_idtyp() throws Exception {
        final JWTClaimsSet claims = appOnlyClaims().build();
        assertThat(claims.getClaim("idtyp")).isNull();

        assertThat(validator.validate(bearer(claims)).clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("The client id is the azp claim, never the oid claim")
    void client_id_is_azp_not_oid() throws Exception {
        final CallerIdentity caller = validator.validate(validBearer());

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID).isNotEqualTo(OBJECT_ID);
    }

    @Test
    @DisplayName("Expiry inside the configured clock skew is tolerated")
    void accepts_expiry_within_clock_skew() throws Exception {
        final JWTClaimsSet claims = appOnlyClaims()
            .expirationTime(Date.from(Instant.now().minusSeconds(CLOCK_SKEW_SECONDS / 2)))
            .build();

        assertThat(validator.validate(bearer(claims)).clientId()).isEqualTo(CLIENT_ID);
    }

    @Test
    @DisplayName("The Bearer scheme is matched case-insensitively")
    void accepts_lowercase_bearer_scheme() throws Exception {
        final String header = validBearer().replace("Bearer ", "bearer ");

        assertThat(validator.validate(header).clientId()).isEqualTo(CLIENT_ID);
    }

    // --- Header and scheme handling ---

    @Test
    @DisplayName("Missing Authorization header rejected")
    void rejects_missing_header() {
        assertRejected(null, TokenRejectionReason.MISSING_AUTHORIZATION_HEADER);
    }

    @Test
    @DisplayName("Blank Authorization header rejected")
    void rejects_blank_header() {
        assertRejected("   ", TokenRejectionReason.MISSING_AUTHORIZATION_HEADER);
    }

    @Test
    @DisplayName("Non-Bearer scheme rejected")
    void rejects_non_bearer_scheme() {
        assertRejected("Basic dXNlcjpwYXNz", TokenRejectionReason.UNSUPPORTED_SCHEME);
    }

    @Test
    @DisplayName("A scheme merely starting with Bearer is rejected")
    void rejects_scheme_prefixed_with_bearer() {
        assertRejected("BearerToken abc", TokenRejectionReason.UNSUPPORTED_SCHEME);
    }

    @Test
    @DisplayName("Empty bearer token rejected")
    void rejects_empty_bearer_token() {
        assertRejected("Bearer   ", TokenRejectionReason.MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("Structurally malformed token rejected")
    void rejects_structurally_malformed_token() {
        assertRejected("Bearer not-a-jwt", TokenRejectionReason.MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("A token whose payload is not JSON rejected")
    void rejects_token_with_non_json_payload() throws JOSEException {
        // Correctly signed with the real key, so the rejection comes from parsing the payload
        // rather than from key selection failing first.
        final JWSObject jws = new JWSObject(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY_ID).build(),
            new Payload("not-json"));
        jws.sign(new RSASSASigner(SIGNING_KEY));

        assertRejected("Bearer " + jws.serialize(), TokenRejectionReason.MALFORMED_TOKEN);
    }

    // --- Signature: the attack cases ---

    @Test
    @DisplayName("An unsigned token (alg none) is rejected")
    void rejects_unsigned_token() {
        assertRejected("Bearer " + new PlainJWT(appOnlyClaims().build()).serialize(),
            TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("HS256 signed with the JWKS RSA public key is rejected")
    void rejects_algorithm_confusion_with_public_key_as_hmac_secret() throws JOSEException {
        final byte[] publicKeyBytes = SIGNING_KEY.toRSAPublicKey().getEncoded();
        final SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(SIGNING_KEY_ID).build(),
            appOnlyClaims().build());
        jwt.sign(new MACSigner(publicKeyBytes));

        assertRejected("Bearer " + jwt.serialize(), TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("A token signed by an unrelated key is rejected")
    void rejects_token_signed_by_unrelated_key() {
        final String token = sign(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(SIGNING_KEY_ID).build(),
            UNRELATED_KEY,
            appOnlyClaims().build());

        assertRejected("Bearer " + token, TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("A tampered signature is rejected")
    void rejects_tampered_signature() {
        final String token = sign(SIGNING_KEY, appOnlyClaims().build());
        final String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertRejected("Bearer " + tampered, TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("An unknown kid is rejected")
    void rejects_unknown_kid() {
        final String token = sign(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("no-such-key").build(),
            SIGNING_KEY,
            appOnlyClaims().build());

        assertRejected("Bearer " + token, TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("An unsupported critical header is rejected")
    void rejects_unsupported_critical_header() {
        final String token = sign(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(SIGNING_KEY_ID)
                .criticalParams(java.util.Set.of("unsupported-crit"))
                .customParam("unsupported-crit", "value")
                .build(),
            SIGNING_KEY,
            appOnlyClaims().build());

        assertRejected("Bearer " + token, TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("Key material supplied in the token header is ignored")
    void ignores_key_material_supplied_in_header() {
        final String token = sign(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(UNRELATED_KEY.getKeyID())
                .jwk(UNRELATED_KEY.toPublicJWK())
                .jwkURL(URI.create("https://attacker.example/keys"))
                .build(),
            UNRELATED_KEY,
            appOnlyClaims().build());

        assertRejected("Bearer " + token, TokenRejectionReason.INVALID_SIGNATURE);
    }

    // --- Audience and issuer ---

    @Test
    @DisplayName("A Microsoft Graph token is rejected on audience")
    void rejects_graph_token() {
        assertRejected(bearer(appOnlyClaims().audience(GRAPH_AUDIENCE).build()),
            TokenRejectionReason.INVALID_AUDIENCE);
    }

    @Test
    @DisplayName("A token minted for a sibling CP API is rejected on audience")
    void rejects_sibling_api_token() {
        assertRejected(bearer(appOnlyClaims().audience(SIBLING_API_AUDIENCE).build()),
            TokenRejectionReason.INVALID_AUDIENCE);
    }

    @Test
    @DisplayName("A token carrying this audience alongside another is rejected")
    void rejects_multi_valued_audience() {
        assertRejected(bearer(appOnlyClaims().audience(List.of(AUDIENCE, SIBLING_API_AUDIENCE)).build()),
            TokenRejectionReason.INVALID_AUDIENCE);
    }

    @Test
    @DisplayName("A wrong issuer is rejected")
    void rejects_wrong_issuer() {
        assertRejected(bearer(appOnlyClaims().issuer("https://attacker.example/v2.0").build()),
            TokenRejectionReason.INVALID_ISSUER);
    }

    @Test
    @DisplayName("An issuer that merely starts with the expected value is rejected")
    void rejects_issuer_matching_only_as_prefix() {
        assertRejected(bearer(appOnlyClaims().issuer(ISSUER + ".attacker.example").build()),
            TokenRejectionReason.INVALID_ISSUER);
    }

    // --- Time and tenancy ---

    @Test
    @DisplayName("An expired token is rejected")
    void rejects_expired_token() {
        assertRejected(bearer(appOnlyClaims()
                .expirationTime(Date.from(Instant.now().minus(Duration.ofHours(2))))
                .build()),
            TokenRejectionReason.EXPIRED);
    }

    @Test
    @DisplayName("A token without exp is rejected - exp is required, not optional")
    void rejects_token_without_expiry() {
        assertRejected(bearer(appOnlyClaims().expirationTime(null).build()),
            TokenRejectionReason.MISSING_EXPIRY);
    }

    @Test
    @DisplayName("A not-yet-valid token is rejected")
    void rejects_not_yet_valid_token() {
        assertRejected(bearer(appOnlyClaims()
                .notBeforeTime(Date.from(Instant.now().plus(Duration.ofHours(1))))
                .build()),
            TokenRejectionReason.NOT_YET_VALID);
    }

    @Test
    @DisplayName("A wrong tenant id is rejected")
    void rejects_wrong_tenant() {
        assertRejected(bearer(appOnlyClaims().claim("tid", "99999999-9999-9999-9999-999999999999").build()),
            TokenRejectionReason.INVALID_TENANT);
    }

    @Test
    @DisplayName("A v1.0 token is rejected")
    void rejects_v1_token() {
        assertRejected(bearer(appOnlyClaims().claim("ver", "1.0").build()),
            TokenRejectionReason.UNSUPPORTED_TOKEN_VERSION);
    }

    // --- Identity and app-only ---

    @Test
    @DisplayName("A missing azp claim is rejected")
    void rejects_missing_azp() {
        assertRejected(bearer(appOnlyClaims().claim("azp", null).build()),
            TokenRejectionReason.INVALID_CLIENT_ID);
    }

    @Test
    @DisplayName("A non-UUID azp claim is rejected")
    void rejects_non_uuid_azp() {
        assertRejected(bearer(appOnlyClaims().claim("azp", "not-a-uuid").build()),
            TokenRejectionReason.INVALID_CLIENT_ID);
    }

    @Test
    @DisplayName("A delegated token is rejected on sub != oid")
    void rejects_delegated_token_on_subject_mismatch() {
        assertRejected(bearer(appOnlyClaims().subject("99999999-9999-9999-9999-999999999999").build()),
            TokenRejectionReason.DELEGATED_TOKEN);
    }

    @Test
    @DisplayName("A token carrying scp is rejected")
    void rejects_token_with_scp() {
        assertRejected(bearer(appOnlyClaims().claim("scp", "CourtHouses.Read").build()),
            TokenRejectionReason.DELEGATED_TOKEN);
    }

    @Test
    @DisplayName("A token without roles is rejected")
    void rejects_token_without_roles() {
        assertRejected(bearer(appOnlyClaims().claim("roles", null).build()),
            TokenRejectionReason.MISSING_ROLE);
    }

    @Test
    @DisplayName("A token with an empty roles array is rejected")
    void rejects_token_with_empty_roles() {
        assertRejected(bearer(appOnlyClaims().claim("roles", List.of()).build()),
            TokenRejectionReason.MISSING_ROLE);
    }

    @Test
    @DisplayName("A token carrying only unrecognised roles is rejected")
    void rejects_token_with_unrecognised_role() {
        assertRejected(bearer(appOnlyClaims().claim("roles", List.of("Some.Other.Role")).build()),
            TokenRejectionReason.MISSING_ROLE);
    }

    @Test
    @DisplayName("A missing role is a 403, an unusable token is a 401")
    void maps_missing_role_to_forbidden_and_bad_token_to_unauthorized() {
        assertThat(TokenRejectionReason.MISSING_ROLE.getStatus().value()).isEqualTo(403);
        assertThat(TokenRejectionReason.MISSING_ROLE.getErrorCode()).isEqualTo("insufficient_scope");
        assertThat(TokenRejectionReason.INVALID_SIGNATURE.getStatus().value()).isEqualTo(401);
        assertThat(TokenRejectionReason.INVALID_SIGNATURE.getErrorCode()).isEqualTo("invalid_token");
    }

    // --- Non-enforcing modes and leakage ---

    @Test
    @DisplayName("The unverified extraction path validates nothing and flags the caller unverified")
    void unverified_identity_carries_no_client_id() {
        final CallerIdentity caller = validator.unverifiedIdentity();

        assertThat(caller.verified()).isFalse();
        assertThat(caller.clientId()).isNull();
    }

    @Test
    @DisplayName("The exception never carries token material")
    void exception_never_carries_token_material() {
        final String header = validBearer();
        final String token = header.substring("Bearer ".length());

        assertThatThrownBy(() -> validator.validate(header.replace(token, token.substring(0, token.length() - 4) + "AAAA")))
            .isInstanceOf(TokenValidationException.class)
            .satisfies(e -> {
                assertThat(e.getMessage()).doesNotContain(token).isEqualTo("INVALID_SIGNATURE");
                assertThat(e.getCause()).isNull();
            });
    }
}
