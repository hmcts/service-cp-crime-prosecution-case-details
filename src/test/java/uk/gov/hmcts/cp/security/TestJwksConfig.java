package uk.gov.hmcts.cp.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestJwksConfig {

    // Registered under its own name and marked @Primary rather than overriding the production bean
    // by name: a name override that silently loses leaves every negative test passing for the wrong
    // reason, against the real Entra endpoint.
    @Bean
    @Primary
    public JWKSource<SecurityContext> testJwkSource() {
        return new ImmutableJWKSet<>(new JWKSet(TestTokens.SIGNING_KEY.toPublicJWK()));
    }
}
