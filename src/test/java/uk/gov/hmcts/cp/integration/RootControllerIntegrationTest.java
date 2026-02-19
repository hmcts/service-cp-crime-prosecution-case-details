package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.repositories.ExampleRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RootControllerIntegrationTest extends IntegrationTestBase {

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
    void root_endpoint_should_be_ok() throws Exception {
        mockMvc.perform(get("/example/{example_id}", entity.getId()))
                .andExpect(status().isOk());
    }
}