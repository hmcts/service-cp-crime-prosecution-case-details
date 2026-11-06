package uk.gov.hmcts.cp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.hmcts.cp.config.AuthProperties;

import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.cp.security.TestTokens.AUDIENCE;
import static uk.gov.hmcts.cp.security.TestTokens.CLIENT_ID;
import static uk.gov.hmcts.cp.security.TestTokens.ROLE;
import static uk.gov.hmcts.cp.security.TestTokens.TENANT_ID;
import static uk.gov.hmcts.cp.security.TestTokens.appOnlyClaims;

/** One pod trusting primary and CP: each tenant has its own key, so issuers must never mix. */
class EntraTokenValidatorDualIssuerTest {

    private static final String CP_TENANT_ID = "66666666-6666-6666-6666-666666666666";
    private static final String CP_AUDIENCE = "77777777-7777-7777-7777-777777777777";
    private static final String CP_ISSUER = issuerFor(CP_TENANT_ID);
    private static final String KEY_ID = "shared-kid";

    private static RSAKey primaryKey;
    private static RSAKey cpKey;
    private static JwkSourceFactory jwkSources;
    private static EntraTokenValidator dualValidator;
    private static EntraTokenValidator primaryOnlyValidator;

    @BeforeAll
    static void setUp() throws JOSEException {
        // Same kid in both tenants on purpose: a key must be found by issuer, never by kid alone.
        primaryKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        cpKey = new RSAKeyGenerator(2048).keyID(KEY_ID).generate();
        final Map<String, JWKSource<SecurityContext>> keysByUri = Map.of(
            jwksUriFor(TENANT_ID), new ImmutableJWKSet<>(new JWKSet(primaryKey.toPublicJWK())),
            jwksUriFor(CP_TENANT_ID), new ImmutableJWKSet<>(new JWKSet(cpKey.toPublicJWK())));
        jwkSources = keysByUri::get;

        dualValidator = new EntraTokenValidator(jwkSources, properties(CP_TENANT_ID, CP_AUDIENCE));
        primaryOnlyValidator = new EntraTokenValidator(jwkSources, properties("", ""));
    }

    @Test
    @DisplayName("A primary token for the primary audience is accepted and labelled primary")
    void accepts_primary_token() throws TokenValidationException {
        final CallerIdentity caller = dualValidator.validate(primaryToken(claims -> { }));

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
        assertThat(caller.issuerName()).isEqualTo("primary");
    }

    @Test
    @DisplayName("A CP token for the CP audience is accepted and labelled cp")
    void accepts_cp_token() throws TokenValidationException {
        final CallerIdentity caller = dualValidator.validate(cpToken(claims -> { }));

        assertThat(caller.clientId()).isEqualTo(CLIENT_ID);
        assertThat(caller.issuerName()).isEqualTo("cp");
    }

    @Test
    @DisplayName("CP in the primary tenant accepts both audiences and is labelled primary+cp")
    void same_tenant_is_labelled_primary_plus_cp() throws TokenValidationException {
        final EntraTokenValidator merged = new EntraTokenValidator(jwkSources, properties(TENANT_ID, CP_AUDIENCE));

        assertThat(merged.validate(primaryToken(claims -> { })).issuerName()).isEqualTo("primary+cp");
        assertThat(merged.validate(primaryToken(claims -> claims.audience(CP_AUDIENCE))).issuerName())
            .isEqualTo("primary+cp");
    }

    @Test
    @DisplayName("A CP token minted for the primary audience is rejected")
    void rejects_cp_token_for_primary_audience() {
        assertRejected(dualValidator, cpToken(claims -> claims.audience(AUDIENCE)), TokenRejectionReason.INVALID_AUDIENCE);
    }

    @Test
    @DisplayName("A primary token minted for the CP audience is rejected")
    void rejects_primary_token_for_cp_audience() {
        assertRejected(dualValidator, primaryToken(claims -> claims.audience(CP_AUDIENCE)),
            TokenRejectionReason.INVALID_AUDIENCE);
    }

    @Test
    @DisplayName("A token claiming the CP issuer but signed with the primary key is rejected")
    void rejects_cp_issuer_signed_with_primary_key() {
        assertRejected(dualValidator, sign(primaryKey, cpClaims()), TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("A token claiming the primary issuer but signed with the CP key is rejected")
    void rejects_primary_issuer_signed_with_cp_key() {
        assertRejected(dualValidator, sign(cpKey, appOnlyClaims()), TokenRejectionReason.INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("The CP issuer with the primary tenant id is rejected on tid")
    void rejects_cp_issuer_with_primary_tenant() {
        assertRejected(dualValidator, cpToken(claims -> claims.claim("tid", TENANT_ID)),
            TokenRejectionReason.INVALID_TENANT);
    }

    @Test
    @DisplayName("An issuer that is neither primary nor CP is rejected")
    void rejects_unknown_issuer() {
        assertRejected(dualValidator,
            primaryToken(claims -> claims.issuer(issuerFor("99999999-9999-9999-9999-999999999999"))),
            TokenRejectionReason.INVALID_ISSUER);
    }

    @Test
    @DisplayName("A token with no iss is rejected")
    void rejects_missing_issuer() {
        assertRejected(dualValidator, primaryToken(claims -> claims.issuer(null)), TokenRejectionReason.INVALID_ISSUER);
    }

    @Test
    @DisplayName("With the CP pair removed, a genuine CP token is rejected")
    void rejects_cp_token_when_cp_not_configured() {
        assertRejected(primaryOnlyValidator, cpToken(claims -> { }), TokenRejectionReason.INVALID_ISSUER);
    }

    private static AuthProperties properties(final String cpTenantId, final String cpAudience) {
        return new AuthProperties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, "", "", cpTenantId, cpAudience, "", "",
            60L, 3600L, ROLE, new MockEnvironment());
    }

    private static void assertRejected(final EntraTokenValidator validator, final String header,
                                       final TokenRejectionReason reason) {
        assertThatThrownBy(() -> validator.validate(header))
            .isInstanceOf(TokenValidationException.class)
            .extracting(e -> ((TokenValidationException) e).getReason())
            .isEqualTo(reason);
    }

    private static JWTClaimsSet.Builder cpClaims() {
        return appOnlyClaims().issuer(CP_ISSUER).claim("tid", CP_TENANT_ID).audience(CP_AUDIENCE);
    }

    private static String primaryToken(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder builder = appOnlyClaims();
        customiser.accept(builder);
        return sign(primaryKey, builder);
    }

    private static String cpToken(final Consumer<JWTClaimsSet.Builder> customiser) {
        final JWTClaimsSet.Builder builder = cpClaims();
        customiser.accept(builder);
        return sign(cpKey, builder);
    }

    private static String sign(final RSAKey key, final JWTClaimsSet.Builder claims) {
        return "Bearer " + TestTokens.sign(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY_ID).build(), key, claims.build());
    }

    private static String issuerFor(final String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    private static String jwksUriFor(final String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
    }
}
