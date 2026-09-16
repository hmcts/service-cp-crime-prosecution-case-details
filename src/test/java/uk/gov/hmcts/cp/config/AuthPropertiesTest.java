package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.hmcts.cp.security.AuthMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthPropertiesTest {

    private static final String TENANT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String AUDIENCE = "22222222-2222-2222-2222-222222222222";
    private static final String ROLES = "app.read";

    private static MockEnvironment environment(final String... profiles) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static AuthProperties properties(final AuthMode mode, final String tenantId, final String audience,
                                             final String roles, final long clockSkewSeconds,
                                             final MockEnvironment environment) {
        return new AuthProperties(mode, tenantId, audience, "", "", clockSkewSeconds, 3600L, roles, environment);
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
}
