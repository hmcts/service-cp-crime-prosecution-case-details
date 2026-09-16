package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.security.TestTokens.validBearer;

class ActuatorEndpointsIntegrationTest extends IntegrationTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health", "/actuator/info", "/actuator/prometheus"})
    @DisplayName("Exposed actuator endpoints answer without a token")
    void actuator_endpoints_answer_without_a_token(final String path) throws Exception {
        // Probes and scrapers send no Authorization header, so every exposed endpoint has to answer
        // without one even though auth.mode is ENFORCE here.
        mockMvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Info reports the build details this service was assembled from")
    void info_reports_build_details() throws Exception {
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.build.name").value("service-cp-crime-prosecution-case-details"));
    }

    @Test
    @DisplayName("Auth counters are actually scrapeable, not merely registered")
    void auth_counters_are_scrapeable() throws Exception {
        mockMvc.perform(get("/cases/ABCD1234567").header(AUTHORIZATION, validBearer()));
        mockMvc.perform(get("/cases/ABCD1234567")).andExpect(status().isUnauthorized());

        final String scrape = mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(scrape)
            .contains("auth_token_validation_failure_total")
            .contains("MISSING_AUTHORIZATION_HEADER");
    }
}
