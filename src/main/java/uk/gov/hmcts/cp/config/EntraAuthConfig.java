package uk.gov.hmcts.cp.config;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

@Configuration
public class EntraAuthConfig {

    private static final long JWKS_REFRESH_TIMEOUT_MILLIS = Duration.ofSeconds(15).toMillis();
    private static final long JWKS_MIN_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    @Bean
    public JWKSource<SecurityContext> entraJwkSource(final AuthProperties authProperties) throws MalformedURLException {
        return JWKSourceBuilder.create(URI.create(authProperties.getJwksUri()).toURL())
            .cache(Duration.ofSeconds(authProperties.getJwksCacheTtlSeconds()).toMillis(), JWKS_REFRESH_TIMEOUT_MILLIS)
            .rateLimited(JWKS_MIN_REFRESH_INTERVAL_MILLIS)
            .retrying(true)
            .build();
    }
}
