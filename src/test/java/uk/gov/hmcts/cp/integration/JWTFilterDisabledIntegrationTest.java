package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.repositories.ExampleRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JWTFilterDisabledIntegrationTest extends IntegrationTestBase {

    @Autowired
    ExampleRepository exampleRepository;

    private ExampleEntity entity;

    @BeforeEach
    void setup() {
        entity = exampleRepository.save(
                ExampleEntity.builder()
                        .exampleText("Welcome to service-hmcts-springboot-template")
                        .build()
        );
    }

    @Test
    void filter_should_not_failed_when_disabled() throws Exception {
        mockMvc.perform(
                        MockMvcRequestBuilders.get("/example/{example_id}", entity.getId())
                )
                .andExpectAll(
                        status().isOk(),
                        content().string(containsString("Welcome to service-hmcts-springboot-template"))
                );
    }
}
