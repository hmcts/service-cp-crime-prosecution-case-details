package uk.gov.hmcts.cp.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.security.AuthMode;
import uk.gov.hmcts.cp.security.TrustedIssuer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code auth.*} is the primary issuer (required). {@code auth.cp-*} is the CP issuer (optional);
 * blank means primary-issuer tokens only.
 */
@Component
@Getter
public class AuthProperties {

    public static final String PRIMARY_ISSUER_NAME = "primary";
    public static final String CP_ISSUER_NAME = "cp";

    private static final long MAX_CLOCK_SKEW_SECONDS = 300L;
    private static final Set<String> NON_ENFORCING_PROFILES = Set.of("local", "test");
    private static final String ISSUER_TEMPLATE = "https://login.microsoftonline.com/%s/v2.0";
    private static final String JWKS_URI_TEMPLATE = "https://login.microsoftonline.com/%s/discovery/v2.0/keys";

    private final AuthMode mode;
    private final String tenantId;
    private final String audience;
    private final String issuer;
    private final String jwksUri;

    /**
     * CP issuing tenant. AMP-1384: CP tokens are accepted until consumers onboarded via CP APIM have
     * migrated; AMP-1385 removes this. Blank means CP tokens are rejected.
     */
    private final String cpTenantId;

    /** This API's own audience in the CP tenant. */
    private final String cpAudience;

    /** Derived from {@link #cpTenantId} when not set. */
    private final String cpIssuer;

    /** Derived from {@link #cpTenantId} when not set. */
    private final String cpJwksUri;

    private final long clockSkewSeconds;
    private final long jwksCacheTtlSeconds;
    private final Set<String> roles;

    /** Primary first, then CP when configured. */
    private final List<TrustedIssuer> trustedIssuers;

    public AuthProperties(
        @Value("${auth.mode}") final AuthMode mode,
        @Value("${auth.tenant-id:}") final String tenantId,
        @Value("${auth.audience:}") final String audience,
        @Value("${auth.issuer:}") final String issuer,
        @Value("${auth.jwks-uri:}") final String jwksUri,
        @Value("${auth.cp-tenant-id:}") final String cpTenantId,
        @Value("${auth.cp-audience:}") final String cpAudience,
        @Value("${auth.cp-issuer:}") final String cpIssuer,
        @Value("${auth.cp-jwks-uri:}") final String cpJwksUri,
        @Value("${auth.clock-skew-seconds:60}") final long clockSkewSeconds,
        @Value("${auth.jwks-cache-ttl-seconds:3600}") final long jwksCacheTtlSeconds,
        @Value("${auth.roles:}") final String roles,
        final Environment environment) {

        this.mode = mode;
        this.tenantId = normaliseId(tenantId);
        this.audience = audience.trim();
        this.clockSkewSeconds = clockSkewSeconds;
        this.jwksCacheTtlSeconds = jwksCacheTtlSeconds;
        this.roles = Arrays.stream(roles.split(","))
            .map(String::trim)
            .filter(role -> !role.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
        this.issuer = issuer.isBlank() ? String.format(ISSUER_TEMPLATE, this.tenantId) : issuer.trim();
        this.jwksUri = jwksUri.isBlank() ? String.format(JWKS_URI_TEMPLATE, this.tenantId) : jwksUri.trim();

        this.cpTenantId = normaliseId(cpTenantId);
        this.cpAudience = cpAudience.trim();
        this.cpIssuer = derivedOrBlank(cpIssuer, this.cpTenantId, ISSUER_TEMPLATE);
        this.cpJwksUri = derivedOrBlank(cpJwksUri, this.cpTenantId, JWKS_URI_TEMPLATE);

        rejectNonEnforcingModeOutsideLocalAndTest(mode, environment);
        rejectHalfSetCpPair();
        rejectIncompleteConfiguration();
        this.trustedIssuers = buildTrustedIssuers();
    }

    public final boolean isCpIssuerEnabled() {
        return !cpTenantId.isEmpty();
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

    /** All-or-nothing in every mode: a half-set CP pair is a misconfiguration, never silently ignored. */
    private void rejectHalfSetCpPair() {
        if (isCpAudienceMissing()) {
            throw new IllegalStateException("auth.cp-audience must be set when auth.cp-tenant-id is set");
        }
        if (hasCpValuesWithoutTenant()) {
            throw new IllegalStateException("auth.cp-tenant-id must be set when any other auth.cp-* value is set");
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
        if (sharesPrimaryIssuer() && !matchesPrimaryTenantAndKeys()) {
            throw new IllegalStateException("auth.cp-issuer equals auth.issuer but the tenant or JWKS URI differs");
        }
    }

    private boolean isCpAudienceMissing() {
        return isCpIssuerEnabled() && cpAudience.isEmpty();
    }

    private boolean hasCpValuesWithoutTenant() {
        return !isCpIssuerEnabled() && (!cpAudience.isEmpty() || !cpIssuer.isEmpty() || !cpJwksUri.isEmpty());
    }

    /** CP configured in the same tenant as the primary issuer, so both produce the same {@code iss}. */
    private boolean sharesPrimaryIssuer() {
        return isCpIssuerEnabled() && cpIssuer.equalsIgnoreCase(issuer);
    }

    private boolean matchesPrimaryTenantAndKeys() {
        return cpTenantId.equals(tenantId) && cpJwksUri.equalsIgnoreCase(jwksUri);
    }

    /** Same tenant for both means one {@code iss}, so it becomes one issuer accepting both audiences. */
    private List<TrustedIssuer> buildTrustedIssuers() {
        final TrustedIssuer primary =
            new TrustedIssuer(PRIMARY_ISSUER_NAME, issuer, tenantId, Set.of(audience), jwksUri);
        final List<TrustedIssuer> issuers;
        if (sharesPrimaryIssuer()) {
            issuers = List.of(new TrustedIssuer(PRIMARY_ISSUER_NAME + "+" + CP_ISSUER_NAME,
                issuer, tenantId, Set.of(audience, cpAudience), jwksUri));
        } else if (isCpIssuerEnabled()) {
            issuers = List.of(primary,
                new TrustedIssuer(CP_ISSUER_NAME, cpIssuer, cpTenantId, Set.of(cpAudience), cpJwksUri));
        } else {
            issuers = List.of(primary);
        }
        return issuers;
    }

    /** An explicit value wins; otherwise derived from the tenant, or blank when no tenant is set. */
    private static String derivedOrBlank(final String explicit, final String tenant, final String template) {
        final String value;
        if (explicit.isBlank() && tenant.isEmpty()) {
            value = "";
        } else if (explicit.isBlank()) {
            value = String.format(template, tenant);
        } else {
            value = explicit.trim();
        }
        return value;
    }

    /** Tenant ids are GUIDs: trimmed and lower-cased, as Entra emits {@code tid}. */
    private static String normaliseId(final String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
