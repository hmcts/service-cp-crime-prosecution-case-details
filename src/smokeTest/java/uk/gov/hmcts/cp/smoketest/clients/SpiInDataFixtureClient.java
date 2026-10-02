package uk.gov.hmcts.cp.smoketest.clients;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.smoketest.config.SmokeTestProperties;

@Component
@RequiredArgsConstructor
public class SpiInDataFixtureClient {

    private static final String SPI_IN_PATH = "/stagingprosecutorsspi-service/CJSEService";
    private static final String SUCCESS_MARKER = "<ResponseCode>1</ResponseCode>";

    private final SmokeTestProperties properties;
    private final RestTemplate restTemplate;

    public ResponseEntity<String> submit(final String soapEnvelope) {
        final String url = properties.getBackendBaseUrl() + SPI_IN_PATH;
        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/soap+xml;charset=UTF-8"));
        headers.set("CJSCPPUID", properties.getCjscppuid());

        final ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(soapEnvelope, headers), String.class);

        if (response.getBody() == null || !response.getBody().contains(SUCCESS_MARKER)) {
            throw new IllegalStateException("SPI-IN submission did not return success marker: " + response.getBody());
        }
        return response;
    }
}