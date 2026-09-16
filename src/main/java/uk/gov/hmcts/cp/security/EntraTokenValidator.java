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
import java.util.regex.Pattern;

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

    private final DefaultJWTProcessor<SecurityContext> jwtProcessor;
    private final AuthProperties authProperties;

    public EntraTokenValidator(final JWKSource<SecurityContext> jwkSource, final AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.jwtProcessor = new DefaultJWTProcessor<>();
        // RS256 is pinned here rather than read from the token header: Entra's JWKS entries carry no
        // "alg", so the header is the only other source and it is attacker-supplied.
        this.jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource));
        this.jwtProcessor.setJWTClaimsSetVerifier((claims, context) -> { });
    }

    public CallerIdentity validate(final String authorizationHeader) throws TokenValidationException {
        final JWTClaimsSet claims = parseAndVerifySignature(extractBearerToken(authorizationHeader));
        verifyAudience(claims);
        verifyIssuerAndTenant(claims);
        verifyValidityWindow(claims);
        verifyAppOnly(claims);
        return new CallerIdentity(verifyClientId(claims), true);
    }

    public CallerIdentity unverifiedIdentity() {
        return new CallerIdentity(null, false);
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

    private JWTClaimsSet parseAndVerifySignature(final String token) throws TokenValidationException {
        final JWT jwt = parse(token);
        // An unsecured (alg: none) or encrypted token never reaches the verifier at all.
        if (!(jwt instanceof SignedJWT signedJwt)) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_SIGNATURE);
        }
        return verifySignature(signedJwt);
    }

    private static JWT parse(final String token) throws TokenValidationException {
        try {
            return JWTParser.parse(token);
        } catch (ParseException e) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        }
    }

    private JWTClaimsSet verifySignature(final SignedJWT signedJwt) throws TokenValidationException {
        try {
            return jwtProcessor.process(signedJwt, null);
        } catch (BadJWTException e) {
            throw new TokenValidationException(TokenRejectionReason.MALFORMED_TOKEN);
        } catch (BadJOSEException | JOSEException e) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_SIGNATURE);
        }
    }

    private void verifyAudience(final JWTClaimsSet claims) throws TokenValidationException {
        final List<String> audience = claims.getAudience();
        if (audience.size() != 1 || !audience.get(0).equals(authProperties.getAudience())) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_AUDIENCE);
        }
    }

    private void verifyIssuerAndTenant(final JWTClaimsSet claims) throws TokenValidationException {
        // Exact match only: a prefix or contains match admits ".../v2.0.attacker.example".
        if (!authProperties.getIssuer().equals(claims.getIssuer())) {
            throw new TokenValidationException(TokenRejectionReason.INVALID_ISSUER);
        }
        if (!authProperties.getTenantId().equals(stringClaim(claims, CLAIM_TID))) {
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
