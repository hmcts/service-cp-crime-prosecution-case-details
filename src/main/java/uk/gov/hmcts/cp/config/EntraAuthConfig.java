package uk.gov.hmcts.cp.config;

import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import uk.gov.hmcts.cp.security.JwkSourceFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.time.Duration;

@Configuration
public class EntraAuthConfig {

    private static final long JWKS_REFRESH_TIMEOUT_MILLIS = Duration.ofSeconds(15).toMillis();
    private static final long JWKS_MIN_REFRESH_INTERVAL_MILLIS = Duration.ofSeconds(30).toMillis();

    /** One JWKS source per trusted issuer, each with the same cache and refresh settings. */
    @Bean
    public JwkSourceFactory entraJwkSourceFactory(final AuthProperties authProperties) {
        final long cacheTtlMillis = Duration.ofSeconds(authProperties.getJwksCacheTtlSeconds()).toMillis();
        return jwksUri -> {
            try {
                return JWKSourceBuilder.create(URI.create(jwksUri).toURL())
                    .cache(cacheTtlMillis, JWKS_REFRESH_TIMEOUT_MILLIS)
                    .rateLimited(JWKS_MIN_REFRESH_INTERVAL_MILLIS)
                    .retrying(true)
                    .build();
            } catch (MalformedURLException e) {
                throw new IllegalStateException("invalid JWKS URI: " + jwksUri, e);
            }
        };
    }
}
