package uk.gov.hmcts.cp.smoketest.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Getter
public class SmokeTestProperties {

    private final String serviceBaseUrl;
    private final String backendBaseUrl;
    private final String cjscppuid;
    private final long pollTimeoutSeconds;
    private final long pollIntervalMillis;
    private final boolean insecureTls;
    private final String entraTenantId;
    private final String entraClientId;
    private final String entraClientSecret;
    private final String entraScope;
    private final String apimSubscriptionKey;

    public SmokeTestProperties(
            @Value("${smoke.service-base-url}") final String serviceBaseUrl,
            @Value("${progression-client.url}") final String backendBaseUrl,
            @Value("${cjscppuid}") final String cjscppuid,
            @Value("${smoke.poll-timeout-seconds:120}") final long pollTimeoutSeconds,
            @Value("${smoke.poll-interval-millis:5000}") final long pollIntervalMillis,
            @Value("${smoke.insecure-tls:false}") final boolean insecureTls,
            @Value("${smoke.entra-tenant-id:}") final String entraTenantId,
            @Value("${smoke.entra-client-id:}") final String entraClientId,
            @Value("${smoke.entra-client-secret:}") final String entraClientSecret,
            @Value("${smoke.entra-scope:}") final String entraScope,
            @Value("${smoke.apim-subscription-key:}") final String apimSubscriptionKey) {
        this.serviceBaseUrl = serviceBaseUrl;
        this.backendBaseUrl = backendBaseUrl;
        this.cjscppuid = cjscppuid;
        this.pollTimeoutSeconds = pollTimeoutSeconds;
        this.pollIntervalMillis = pollIntervalMillis;
        this.insecureTls = insecureTls;
        this.entraTenantId = entraTenantId;
        this.entraClientId = entraClientId;
        this.entraClientSecret = entraClientSecret;
        this.entraScope = entraScope;
        this.apimSubscriptionKey = apimSubscriptionKey;
    }
}