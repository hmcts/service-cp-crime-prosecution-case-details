package uk.gov.hmcts.cp.smoketest.clients;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;
import uk.gov.hmcts.cp.smoketest.config.SmokeTestProperties;

import java.util.List;
import java.util.Optional;

/**
 * Calls this service's own public contract through the Azure APIM/WAF gateway - the read side
 * doubles as the data-readiness probe (see GetCaseDetailsByCaseUrnSmokeIT), so no separate
 * polling of the upstream case-mapper/progression backends is needed. Identity here is the
 * Entra bearer token + subscription key APIM itself validates - CJSCPPUID is a
 * raw-ingress/legacy-gateway concern (see SpiInDataFixtureClient), not part of this path.
 */
@Component
@RequiredArgsConstructor
public class CaseDetailsSmokeClient {

    private final SmokeTestProperties properties;
    private final RestTemplate restTemplate;
    private final EntraTokenClient entraTokenClient;

    public Optional<ResponseEntity<CaseDetailResponse>> getCaseDetails(final String caseUrn) {
        final String url = properties.getServiceBaseUrl() + "/cases/" + caseUrn;
        final HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(entraTokenClient.fetchAccessToken());
        headers.set("Ocp-Apim-Subscription-Key", properties.getApimSubscriptionKey());
        Optional<ResponseEntity<CaseDetailResponse>> result;
        try {
            result = Optional.of(restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), CaseDetailResponse.class));
        } catch (final HttpClientErrorException.NotFound e) {
            result = Optional.empty();
        }
        return result;
    }
}