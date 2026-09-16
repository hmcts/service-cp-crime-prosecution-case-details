package uk.gov.hmcts.cp.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.cp.security.AuthMode;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "auth.mode=OFF")
@ActiveProfiles("local")
class AuthOffModeContextTest {

    @org.springframework.beans.factory.annotation.Autowired
    private AuthProperties authProperties;

    @Test
    @DisplayName("The context starts in OFF mode with no tenant, audience or roles configured")
    void context_starts_with_auth_off() {
        assertThat(authProperties.getMode()).isEqualTo(AuthMode.OFF);
        assertThat(authProperties.getAudience()).isEmpty();
        assertThat(authProperties.getRoles()).isEmpty();
    }
}
