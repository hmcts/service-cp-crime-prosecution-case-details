package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.security.AuthMode;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Getter
public class AuthProperties {

    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;
    private static final Set<String> NON_ENFORCING_PROFILES = Set.of("local", "test");
    private static final String ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";
    private static final String JWKS_URI_TEMPLATE = "https://login.microsoftonline.com/%s/discovery/v2.0/keys";

    private final AuthMode mode;
    private final String tenantId;
    private final String audience;
    private final String issuer;
    private final String jwksUri;
    private final long clockSkewSeconds;
    private final long jwksCacheTtlSeconds;
    private final Set<String> roles;

    public AuthProperties(
        @Value("${auth.mode}") final AuthMode mode,
        @Value("${auth.tenant-id:}") final String tenantId,
        @Value("${auth.audience:}") final String audience,
        @Value("${auth.issuer:}") final String issuer,
        @Value("${auth.jwks-uri:}") final String jwksUri,
        @Value("${auth.clock-skew-seconds:60}") final long clockSkewSeconds,
        @Value("${auth.jwks-cache-ttl-seconds:3600}") final long jwksCacheTtlSeconds,
        @Value("${auth.roles:}") final String roles,
        final Environment environment) {

        this.mode = mode;
        this.tenantId = tenantId.trim();
        this.audience = audience.trim();
        this.clockSkewSeconds = clockSkewSeconds;
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
        this.roles = Arrays.stream(roles.split(","))
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .collect(Collectors.toUnmodifiableSet());

        rejectNonEnforcingModeOutsideLocalAndTest(mode, environment);
        rejectIncompleteConfiguration();

        this.issuer = issuer.isBlank() ? String.format(ISSUER_TEMPLATE, this.tenantId) : issuer.trim();
        this.jwksUri = jwksUri.isBlank() ? String.format(JWKS_URI_TEMPLATE, this.tenantId) : jwksUri.trim();
    }

    private static void rejectNonEnforcingModeOutsideLocalAndTest(final AuthMode mode, final Environment environment) {
        final boolean nonEnforcingPermitted = Arrays.stream(environment.getActiveProfiles())
            .anyMatch(NON_ENFORCING_PROFILES::contains);
        if (mode != AuthMode.ENFORCE && !nonEnforcingPermitted) {
            throw new IllegalStateException(
                "auth.mode=" + mode + " is only permitted under the local or test profile; "
                    + "a deployed environment must run ENFORCE");
        }
    }

    private void rejectIncompleteConfiguration() {
        if (mode == AuthMode.OFF) {
            return;
        }
        if (tenantId.isEmpty()) {
            throw new IllegalStateException("auth.tenant-id must be set (the token-issuing tenant, not the hosting tenant)");
        }
        if (audience.isEmpty()) {
            throw new IllegalStateException("auth.audience must be set; a blank audience never means accept any audience");
        }
        if (roles.isEmpty()) {
            throw new IllegalStateException("auth.roles must list at least one application role this API recognises");
        }
        if (clockSkewSeconds < 0 || clockSkewSeconds > MAX_CLOCK_SKEW_SECONDS) {
            throw new IllegalStateException("auth.clock-skew-seconds must be between 0 and " + MAX_CLOCK_SKEW_SECONDS);
        }
    }
}
