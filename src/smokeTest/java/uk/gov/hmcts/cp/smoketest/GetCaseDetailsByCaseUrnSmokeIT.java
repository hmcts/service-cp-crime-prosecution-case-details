package uk.gov.hmcts.cp.smoketest;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;
import uk.gov.hmcts.cp.smoketest.clients.CaseDetailsSmokeClient;
import uk.gov.hmcts.cp.smoketest.clients.SpiInDataFixtureClient;
import uk.gov.hmcts.cp.smoketest.config.SmokeTestConfig;
import uk.gov.hmcts.cp.smoketest.config.SmokeTestProperties;
import uk.gov.hmcts.cp.smoketest.evidence.EvidenceRecorder;
import uk.gov.hmcts.cp.smoketest.fixtures.SpiInMessageBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Deliberately not the main Application context - see SmokeTestConfig javadoc. Targets a real
 * deployed instance over the network, so this JVM never boots controllers/filters/web server.
 */
@SpringBootTest(classes = SmokeTestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Slf4j
class GetCaseDetailsByCaseUrnSmokeIT {

    @Autowired
    private SmokeTestProperties properties;

    @Autowired
    private SpiInMessageBuilder spiInMessageBuilder;

    @Autowired
    private SpiInDataFixtureClient spiInClient;

    @Autowired
    private CaseDetailsSmokeClient caseDetailsClient;

    @Autowired
    private EvidenceRecorder evidenceRecorder;

    @Test
    void shouldReturnCaseDetails_whenCaseCreatedViaSpiIn() throws InterruptedException {
        final SpiInMessageBuilder.SpiInFixture fixture = spiInMessageBuilder.build();

        final Instant startedAt = Instant.now();
        final ResponseEntity<String> spiInResponse = spiInClient.submit(fixture.soapEnvelope());
        log.info("SPI-IN submitted for caseUrn:{}", fixture.urn());

        final ResponseEntity<CaseDetailResponse> caseDetailsResponse = pollUntilAvailable(fixture.urn());
        final Instant completedAt = Instant.now();

        assertThat(caseDetailsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        final CaseDetailResponse body = caseDetailsResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCaseStatus()).isNotBlank();

        recordEvidence(fixture.urn(), spiInResponse, caseDetailsResponse, body, startedAt, completedAt);
    }

    private ResponseEntity<CaseDetailResponse> pollUntilAvailable(final String caseUrn) throws InterruptedException {
        final Instant deadline = Instant.now().plusSeconds(properties.getPollTimeoutSeconds());
        Optional<ResponseEntity<CaseDetailResponse>> found = Optional.empty();
        while (Instant.now().isBefore(deadline) && found.isEmpty()) {
            found = caseDetailsClient.getCaseDetails(caseUrn);
            if (found.isEmpty()) {
                Thread.sleep(properties.getPollIntervalMillis());
            }
        }
        return found.orElseGet(() -> fail("Case details for " + caseUrn + " did not become available within "
                + properties.getPollTimeoutSeconds() + "s"));
    }

    private void recordEvidence(final String caseUrn, final ResponseEntity<String> spiInResponse,
                                 final ResponseEntity<CaseDetailResponse> caseDetailsResponse,
                                 final CaseDetailResponse body, final Instant startedAt, final Instant completedAt) {
        final Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("spiInStatus", spiInResponse.getStatusCode().value());
        evidence.put("caseDetailsStatus", caseDetailsResponse.getStatusCode().value());
        evidence.put("caseStatus", body.getCaseStatus());
        evidence.put("reportingRestrictions", body.getReportingRestrictions());
        evidence.put("startedAt", startedAt.toString());
        evidence.put("completedAt", completedAt.toString());
        evidence.put("durationMillis", Duration.between(startedAt, completedAt).toMillis());
        evidenceRecorder.record("getCaseDetailsByCaseUrn", caseUrn, evidence);
    }
}