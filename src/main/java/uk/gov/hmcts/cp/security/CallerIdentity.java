package uk.gov.hmcts.cp.security;

/**
 * The caller resolved from the token.
 *
 * @param issuerName the {@link TrustedIssuer} that validated the token, or {@link #UNVERIFIED_ISSUER}
 */
public record CallerIdentity(String clientId, boolean verified, String issuerName) {

    public static final String UNVERIFIED_ISSUER = "unverified";
}
