package uk.gov.hmcts.cp.smoketest.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

/**
 * Standalone Spring context for smoke tests, mirroring AppConfig's RestTemplate bean style -
 * deliberately not the main Application context, since smoke tests only make outbound calls to
 * a remote deployed instance and never need this JVM's own controllers/filters/web server.
 */
@Configuration
@ComponentScan(basePackages = "uk.gov.hmcts.cp.smoketest")
public class SmokeTestConfig {

    @Bean
    public RestTemplate restTemplate(@Value("${smoke.insecure-tls:false}") final boolean insecureTls) {
        final RestTemplate template;
        if (insecureTls) {
            template = new RestTemplate(insecureRequestFactory());
        } else {
            template = new RestTemplate();
        }
        return template;
    }

    /**
     * Nonlive CP ingress hosts (steccm*, steamp*, devamp*) present certificates the JVM's
     * default trust store doesn't chain to. Pipeline 340's own Maven invocation works around
     * the same gap with -Dmaven.wagon.http.ssl.insecure=true for these exact hosts; this
     * mirrors that, opt-in only via smoke.insecure-tls (SMOKE_INSECURE_TLS), never on by
     * default.
     */
    private SimpleClientHttpRequestFactory insecureRequestFactory() {
        try {
            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] {trustAllTrustManager()}, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
            return new SimpleClientHttpRequestFactory();
        } catch (final GeneralSecurityException e) {
            throw new IllegalStateException("Unable to configure insecure TLS for smoke tests", e);
        }
    }

    private X509TrustManager trustAllTrustManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                // no-op - trust-all, opt-in only for nonlive smoke testing, see class javadoc
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
                // no-op - trust-all, opt-in only for nonlive smoke testing, see class javadoc
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }
}