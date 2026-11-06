package uk.gov.hmcts.cp.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.config.AuthProperties;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The token's {@code iss} picks one {@link TrustedIssuer} (primary or CP); the signature, audience,
 * issuer and tenant are then checked against that issuer only.
 */
@Component
public class EntraTokenValidator {

    private static final String BEARER_PREFIX = "Bearer";
    private static final String CLAIM_AZP = "azp";
    private static final String CLAIM_OID = "oid";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SCP = "scp";
    private static final String CLAIM_TID = "tid";
    private static final String CLAIM_VER = "ver";
    private static final String SUPPORTED_TOKEN_VERSION = "2.0";
    private static final Pattern UUID_PATTERN =
        Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** One signature processor per trusted issuer, keyed by exact {@code iss}. */
    private final Map<String, IssuerVerifier> verifiersByIssuer;
    private final AuthProperties authProperties;

    private record IssuerVerifier(TrustedIssuer trusted, DefaultJWTProcessor<SecurityContext> processor) { }

    public EntraTokenValidator(final JwkSourceFactory jwkSources, final AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.verifiersByIssuer = authProperties.getTrustedIssuers().stream()
            .collect(Collectors.toUnmodifiableMap(TrustedIssuer::issuer, trusted -> new IssuerVerifier(
                trusted, buildProcessor(jwkSources.forJwksUri(trusted.jwksUri())))));
    }

    private static DefaultJWTProcessor<SecurityContext> buildProcessor(final JWKSource<SecurityContext> jwkSource) {
        final DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        // RS256 is pinned here rather than read from the token header: Entra's JWKS entries carry no
        // "alg", so the header is the only other source and it is attacker-supplied.
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> { });
        return jwtProcessor;
    }

    public CallerIdentity validate(final String authorizationHeader) throws TokenValidationException {
        final SignedJWT signedJwt = parseSigned(extractBearerToken(authorizationHeader));
        final IssuerVerifier selected = selectByIssuer(signedJwt);
        final TrustedIssuer trusted = selected.trusted();
        final JWTClaimsSet claims = verifySignature(selected.processor(), signedJwt);
        verifyAudience(claims, trusted);
        verifyIssuerAndTenant(claims, trusted);
        verifyValidityWindow(claims);
        verifyAppOnly(claims);
        return new CallerIdentity(verifyClientId(claims), true, trusted.name());
    }

    public CallerIdentity unverifiedIdentity() {
        return new CallerIdentity(null, false, CallerIdentity.UNVERIFIED_ISSUER);
    }

    private static String extractBearerToken(final String authorizationHeader) throws TokenValidationException {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new TokenValidationException(TokenRejectionReason.MISSING_AUTHORIZATION_HEADER);
        }
        final String trimmed = authorizationHeader.trim();
        // RFC 6750 2.1 makes the scheme case-insensitive; a case-sensitive match rejects legitimate clients.
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new TokenValidationException(TokenRejectionReason.UNSUPPORTED_SCHEME);
        }
        final String remainder = trimmed.substring(BEARER_PREFIX.length());
        if (!remainder.isEmpty() && !Character.isWhitespace(remainder.charAt(0))) {
            throw new TokenValidationException(TokenRejectionReason.UNSUPPORTED_SCHEME);
        }
        final String token = remainder.trim();
        if (token.isEmpty()) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        }
        return token;
    }

    private static SignedJWT parseSigned(final String token) throws TokenValidationException {
        final JWT jwt = parse(token);
        // An unsecured (alg: none) or encrypted token never reaches the verifier at all.
        if (!(jwt instanceof SignedJWT signedJwt)) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_SIGNATURE);
        }
        return signedJwt;
    }

    /**
     * Reads {@code iss} before signature verification only to choose the issuer, whose keys then
     * verify the signature.
     */
    private IssuerVerifier selectByIssuer(final SignedJWT signedJwt) throws TokenValidationException {
        final String unverifiedIssuer;
        try {
            unverifiedIssuer = signedJwt.getJWTClaimsSet().getIssuer();
        } catch (ParseException e) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        }
        final IssuerVerifier selected = unverifiedIssuer == null ? null : verifiersByIssuer.get(unverifiedIssuer);
        if (selected == null) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_ISSUER);
        }
        return selected;
    }

    private static JWT parse(final String token) throws TokenValidationException {
        try {
            return JWTParser.parse(token);
        } catch (ParseException e) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        }
    }

    private static JWTClaimsSet verifySignature(final DefaultJWTProcessor<SecurityContext> jwtProcessor,
                                                final SignedJWT signedJwt) throws TokenValidationException {
        try {
            return jwtProcessor.process(signedJwt, null);
        } catch (BadJWTException e) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        } catch (BadJOSEException | JOSEException e) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_SIGNATURE);
        }
    }

    private static void verifyAudience(final JWTClaimsSet claims, final TrustedIssuer trusted)
        throws TokenValidationException {
        final List<String> audience = claims.getAudience();
        if (audience.size() != 1 || !trusted.audiences().contains(audience.get(0))) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_AUDIENCE);
        }
    }

    private static void verifyIssuerAndTenant(final JWTClaimsSet claims, final TrustedIssuer trusted)
        throws TokenValidationException {
        // Exact match only: a prefix or contains match admits ".../v2.0.attacker.example".
        if (!trusted.issuer().equals(claims.getIssuer())) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_ISSUER);
        }
        if (!trusted.tenantId().equals(stringClaim(claims, CLAIM_TID))) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_TENANT);
        }
        if (!SUPPORTED_TOKEN_VERSION.equals(stringClaim(claims, CLAIM_VER))) {
            throw new TokenValidationException(TokenRejectionReason.UNSUPPORTED_TOKEN_VERSION);
        }
    }

    private void verifyValidityWindow(final JWTClaimsSet claims) throws TokenValidationException {
        // Nimbus exposes exp/nbf as java.util.Date; convert at the call so no Date-typed local exists.
        if (claims.getExpirationTime() == null) {
            throw new TokenValidationException(TokenRejectionReason.MISSING_EXPIRY);
        }
        final Instant now = Instant.now();
        final Duration skew = Duration.ofSeconds(authProperties.getClockSkewSeconds());
        if (claims.getExpirationTime().toInstant().isBefore(now.minus(skew))) {
            throw new TokenValidationException(TokenRejectionReason.EXPIRED);
        }
        if (claims.getNotBeforeTime() != null
            && claims.getNotBeforeTime().toInstant().isAfter(now.plus(skew))) {
            throw new TokenValidationException(TokenRejectionReason.NOT_YET_VALID);
        }
    }

    private void verifyAppOnly(final JWTClaimsSet claims) throws TokenValidationException {
        // App-only is proven by sub == oid, non-empty roles and absent scp. It must never be made to
        // depend on idtyp: Entra omits that claim unless it is enabled as an optional claim, so
        // requiring it would reject all legitimate traffic.
        if (claims.getClaim(CLAIM_SCP) != null) {
            throw new TokenValidationException(TokenRejectionReason.DELEGATED_TOKEN);
        }
        final String subject = claims.getSubject();
        if (subject == null || !subject.equals(stringClaim(claims, CLAIM_OID))) {
            throw new TokenValidationException(TokenRejectionReason.DELEGATED_TOKEN);
        }
        verifyRoles(claims);
    }

    private void verifyRoles(final JWTClaimsSet claims) throws TokenValidationException {
        final Object rolesClaim = claims.getClaim(CLAIM_ROLES);
        if (!(rolesClaim instanceof List<?> roles)
            || roles.stream().noneMatch(role -> authProperties.getRoles().contains(role))) {
            throw new TokenValidationException(TokenRejectionReason.MISSING_ROLE);
        }
    }

    private static String verifyClientId(final JWTClaimsSet claims) throws TokenValidationException {
        // The caller is azp, the calling application's client id, never oid/sub, which is that
        // application's service principal object id in the tenant.
        final String clientId = stringClaim(claims, CLAIM_AZP);
        if (clientId == null || !UUID_PATTERN.matcher(clientId).matches()) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_CLIENT_ID);
        }
        return clientId;
    }

    private static String stringClaim(final JWTClaimsSet claims, final String name) {
        final Object value = claims.getClaim(name);
        return value instanceof String text ? text : null;
    }
}
