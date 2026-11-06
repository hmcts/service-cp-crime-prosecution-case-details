package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.hmcts.cp.security.AuthMode;
import uk.gov.hmcts.cp.security.TrustedIssuer;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest {

    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    private static final String ROLES = "app.read";
    private static final String CP_TENANT_ID = "66666666-6666-6666-6666-666666666666";
    private static final String CP_AUDIENCE = "77777777-7777-7777-7777-777777777777";

    private static MockEnvironment environment(final String... profiles) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static AuthProperties properties(final AuthMode mode, final String tenantId, final String audience,
                                             final String roles, final long clockSkewSeconds,
                                             final MockEnvironment environment) {
        return new AuthProperties(mode, tenantId, audience, "", "", "", "", "", "",
            clockSkewSeconds, 3600L, roles, environment);
    }

    private static AuthProperties withCp(final AuthMode mode, final String cpTenantId, final String cpAudience,
                                         final String cpIssuer, final MockEnvironment environment) {
        return new AuthProperties(mode, TENANT_ID, AUDIENCE, "", "", cpTenantId, cpAudience, cpIssuer, "",
            60L, 3600L, ROLES, environment);
    }

    private static String issuerFor(final String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    private static String jwksUriFor(final String tenantId) {
        return "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
    }

    @Test
    @DisplayName("Issuer and JWKS URI are derived from the configured tenant")
    void derives_issuer_and_jwks_uri_from_tenant() {
        final AuthProperties properties =
            properties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, ROLES, 60L, environment());

        assertThat(properties.getIssuer()).isEqualTo("https://login.microsoftonline.com/" + TENANT_ID + "/v2.0");
        assertThat(properties.getJwksUri())
            .isEqualTo("https://login.microsoftonline.com/" + TENANT_ID + "/discovery/v2.0/keys");
        assertThat(properties.getRoles()).containsExactly(ROLES);
    }

    @Test
    @DisplayName("Startup fails when the audience is blank and the mode is enforcing")
    void fails_startup_on_blank_audience() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, TENANT_ID, "  ", ROLES, 60L, environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.audience");
    }

    @Test
    @DisplayName("Startup fails when the tenant is blank and the mode is enforcing")
    void fails_startup_on_blank_tenant() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, "", AUDIENCE, ROLES, 60L, environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.tenant-id");
    }

    @Test
    @DisplayName("Startup fails when no application role is configured")
    void fails_startup_on_blank_roles() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, " , ", 60L, environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.roles");
    }

    @Test
    @DisplayName("Startup fails for a non-enforcing mode in a deployed environment")
    void fails_startup_on_non_enforcing_mode_without_local_or_test_profile() {
        assertThatThrownBy(() -> properties(AuthMode.OBSERVE, TENANT_ID, AUDIENCE, ROLES, 60L, environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must run ENFORCE");

        assertThatThrownBy(() -> properties(AuthMode.OFF, TENANT_ID, AUDIENCE, ROLES, 60L, environment("dev")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must run ENFORCE");
    }

    @Test
    @DisplayName("A non-enforcing mode is permitted under the local profile")
    void permits_non_enforcing_mode_under_local_profile() {
        assertThat(properties(AuthMode.OFF, "", "", "", 60L, environment("local")).getMode())
            .isEqualTo(AuthMode.OFF);
    }

    @Test
    @DisplayName("Clock skew is capped")
    void fails_startup_on_excessive_clock_skew() {
        assertThatThrownBy(() -> properties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, ROLES, 3600L, environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.clock-skew-seconds");
    }

    // --- CP issuer ---

    @Test
    @DisplayName("With no CP values only the primary issuer is trusted")
    void trusts_only_primary_issuer_by_default() {
        final AuthProperties properties =
            properties(AuthMode.ENFORCE, TENANT_ID, AUDIENCE, ROLES, 60L, environment());

        assertThat(properties.isCpIssuerEnabled()).isFalse();
        assertThat(properties.getTrustedIssuers())
            .extracting(TrustedIssuer::name)
            .containsExactly(AuthProperties.PRIMARY_ISSUER_NAME);
    }

    @Test
    @DisplayName("A CP tenant and audience add a second, separate trusted issuer")
    void cp_pair_adds_second_issuer() {
        final AuthProperties properties = withCp(AuthMode.ENFORCE, CP_TENANT_ID, CP_AUDIENCE, "", environment());

        assertThat(properties.getTrustedIssuers()).containsExactly(
            new TrustedIssuer("primary", issuerFor(TENANT_ID), TENANT_ID, Set.of(AUDIENCE), jwksUriFor(TENANT_ID)),
            new TrustedIssuer("cp", issuerFor(CP_TENANT_ID), CP_TENANT_ID, Set.of(CP_AUDIENCE),
                jwksUriFor(CP_TENANT_ID)));
    }

    @Test
    @DisplayName("Startup fails when a CP tenant is set without a CP audience")
    void fails_startup_on_cp_tenant_without_audience() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, CP_TENANT_ID, "", "", environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.cp-audience must be set");
    }

    @Test
    @DisplayName("Startup fails when a CP audience is set without a CP tenant")
    void fails_startup_on_cp_audience_without_tenant() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, "", CP_AUDIENCE, "", environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.cp-tenant-id must be set");
    }

    @Test
    @DisplayName("Startup fails when a CP issuer is set without a CP tenant")
    void fails_startup_on_cp_issuer_without_tenant() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, "", "", issuerFor(CP_TENANT_ID), environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.cp-tenant-id must be set");
    }

    @Test
    @DisplayName("A half-set CP pair fails startup even in OFF mode")
    void fails_startup_on_half_set_cp_pair_when_off() {
        assertThatThrownBy(() -> withCp(AuthMode.OFF, CP_TENANT_ID, "", "", environment("local")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.cp-audience must be set");
    }

    @Test
    @DisplayName("CP in the same tenant as the primary becomes one issuer accepting both audiences")
    void same_tenant_merges_audiences() {
        final AuthProperties properties = withCp(AuthMode.ENFORCE, TENANT_ID, CP_AUDIENCE, "", environment());

        assertThat(properties.getTrustedIssuers()).singleElement().satisfies(trusted -> {
            assertThat(trusted.name()).isEqualTo("primary+cp");
            assertThat(trusted.tenantId()).isEqualTo(TENANT_ID);
            assertThat(trusted.audiences()).containsExactlyInAnyOrder(AUDIENCE, CP_AUDIENCE);
        });
    }

    @Test
    @DisplayName("The same tenant in a different case still merges into one issuer")
    void same_tenant_in_different_case_merges() {
        final AuthProperties properties =
            withCp(AuthMode.ENFORCE, TENANT_ID.toUpperCase(Locale.ROOT), CP_AUDIENCE, "", environment());

        assertThat(properties.getTrustedIssuers()).singleElement()
            .satisfies(trusted -> assertThat(trusted.audiences()).containsExactlyInAnyOrder(AUDIENCE, CP_AUDIENCE));
    }

    @Test
    @DisplayName("Startup fails when the CP issuer equals the primary issuer but the tenant differs")
    void fails_startup_on_same_issuer_different_tenant() {
        assertThatThrownBy(() -> withCp(AuthMode.ENFORCE, CP_TENANT_ID, CP_AUDIENCE, issuerFor(TENANT_ID), environment()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("auth.cp-issuer equals auth.issuer");
    }

    @Test
    @DisplayName("Tenant ids are trimmed and lower-cased, as Entra emits tid")
    void normalises_tenant_ids() {
        final AuthProperties properties = withCp(AuthMode.ENFORCE,
            "  " + CP_TENANT_ID.toUpperCase(Locale.ROOT) + " ", CP_AUDIENCE, "", environment());

        assertThat(properties.getCpTenantId()).isEqualTo(CP_TENANT_ID);
        assertThat(properties.getCpIssuer()).isEqualTo(issuerFor(CP_TENANT_ID));
    }
}
