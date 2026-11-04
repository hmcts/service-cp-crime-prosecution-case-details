package uk.gov.hmcts.cp.smoketest.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.smoketest.config.SmokeTestProperties;

import java.util.Objects;

/**
 * Fetches an Entra ID (Azure AD) client-credentials bearer token to call this service through
 * the Azure APIM/WAF gateway. Fetched fresh per call - smoke tests are low-frequency, so token
 * caching isn't worth the complexity.
 */
@Component
@RequiredArgsConstructor
public class EntraTokenClient {

    private static final String TOKEN_URL_TEMPLATE = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    private final SmokeTestProperties properties;
    private final RestTemplate restTemplate;

    public String fetchAccessToken() {
        final String url = TOKEN_URL_TEMPLATE.formatted(properties.getEntraTenantId());

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", properties.getEntraClientId());
        body.add("client_secret", properties.getEntraClientSecret());
        body.add("scope", properties.getEntraScope());

        final ResponseEntity<EntraTokenResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), EntraTokenResponse.class);

        return Objects.requireNonNull(response.getBody(), "Entra token response body was null").accessToken();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EntraTokenResponse(@JsonProperty("access_token") String accessToken) {
    }
}