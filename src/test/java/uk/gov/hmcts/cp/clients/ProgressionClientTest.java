package uk.gov.hmcts.cp.clients;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.cp.config.AppPropertiesBackend;
import uk.gov.hmcts.cp.domain.ProgressionResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgressionClientTest {
    @Mock
    AppPropertiesBackend appProperties;
    @Mock
    RestTemplate restTemplate;

    @InjectMocks
    private ProgressionClient progressionClient;

    @Test
    void getProgressionResponseByCaseUrn_shouldReturnValidResponse() {
        ProgressionResponse progressionResponse = Mockito.mock(ProgressionResponse.class);
        when(appProperties.getProgressionUrl()).thenReturn("http://localhost");
        when(appProperties.getProgressionUrl()).thenReturn("/progression-query-api/query/api/rest/progression/prosecutioncases");
        UUID caseUrn = UUID.randomUUID();
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(ProgressionResponse.class)
        )).thenReturn(ResponseEntity.ok(progressionResponse));

        ProgressionResponse response = progressionClient.getProgressionResponse(caseUrn.toString());
        assertEquals(progressionResponse, response);
    }
}