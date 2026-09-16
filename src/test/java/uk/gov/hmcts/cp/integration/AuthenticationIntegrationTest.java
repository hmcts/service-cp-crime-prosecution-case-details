package uk.gov.hmcts.cp.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.cp.security.TestTokens.appOnlyClaims;
import static uk.gov.hmcts.cp.security.TestTokens.bearer;
import static uk.gov.hmcts.cp.security.TestTokens.validBearer;

class AuthenticationIntegrationTest extends IntegrationTestBase {

    private static final String CASE_URL = "/cases/ABCD1234567";

    private WireMockServer wireMockServer;

    @BeforeEach
    void beforeEach() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(8081));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
        stubFor(WireMock.get(urlMatching(".*")).willReturn(aResponse().withStatus(HTTP_NOT_FOUND)));
    }

    @AfterEach
    void afterEach() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    @DisplayName("A token minted by the test key set is accepted and the request reaches the controller")
    void accepts_token_minted_by_the_in_process_key_set() throws Exception {
        // The backend is stubbed to 404, so a 404 here proves the request passed authentication
        // and was handled. Against the wrong key set this would be a 401 instead.
        mockMvc.perform(get(CASE_URL).header(AUTHORIZATION, validBearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("A protected endpoint rejects a request with no token")
    void rejects_request_without_token() throws Exception {
        mockMvc.perform(get(CASE_URL))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string(WWW_AUTHENTICATE, containsString("error=\"invalid_token\"")))
            .andExpect(jsonPath("$.message").value("MISSING_AUTHORIZATION_HEADER"))
            .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("A protected endpoint rejects an invalid token")
    void rejects_request_with_invalid_token() throws Exception {
        mockMvc.perform(get(CASE_URL).header(AUTHORIZATION, "Bearer not-a-jwt"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("MALFORMED_TOKEN"));
    }

    @Test
    @DisplayName("A token without a recognised role is a 403, not a 401")
    void rejects_token_without_recognised_role_as_forbidden() throws Exception {
        final String token = bearer(appOnlyClaims().claim("roles", List.of("Some.Other.Role")).build());

        mockMvc.perform(get(CASE_URL).header(AUTHORIZATION, token))
            .andExpect(status().isForbidden())
            .andExpect(header().string(WWW_AUTHENTICATE, containsString("error=\"insufficient_scope\"")))
            .andExpect(jsonPath("$.message").value("MISSING_ROLE"));
    }

    @Test
    @DisplayName("An expired token is rejected through the real filter chain")
    void rejects_expired_token_through_the_filter_chain() throws Exception {
        final String token = bearer(appOnlyClaims()
            .expirationTime(Date.from(Instant.now().minusSeconds(7200)))
            .build());

        mockMvc.perform(get(CASE_URL).header(AUTHORIZATION, token))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("EXPIRED"));
    }

    @Test
    @DisplayName("Exempt infrastructure endpoints answer without a token")
    void exempt_endpoints_answer_without_a_token() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("A near-miss of an exempt path still requires a token")
    void near_miss_of_exempt_path_requires_a_token() throws Exception {
        mockMvc.perform(get("/actuator/healthx")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("TracingFilter still runs first, so a rejection is traceable")
    void rejection_still_carries_the_trace_id_from_the_request() throws Exception {
        mockMvc.perform(get(CASE_URL).header("traceId", "1234-1234"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("traceId", "1234-1234"));
    }

    @Test
    @DisplayName("The rejection response never echoes the token")
    void rejection_response_never_echoes_the_token() throws Exception {
        final String token = validBearer().substring("Bearer ".length());
        final String tampered = token.substring(0, token.length() - 4) + "AAAA";

        final String body = mockMvc.perform(get(CASE_URL).header(AUTHORIZATION, "Bearer " + tampered))
            .andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(tampered);
    }
}
