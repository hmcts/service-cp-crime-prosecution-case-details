package uk.gov.hmcts.cp.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.repositories.ExampleRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExampleControllerIntegrationTest extends IntegrationTestBase {
    @Autowired
    ExampleRepository exampleRepository;

    @Transactional
    @Test
    void endpoint_should_return_ok() throws Exception {
        ExampleEntity entity = exampleRepository.save(
                ExampleEntity.builder()
                        .exampleText("Welcome to service-hmcts-springboot-template")
                        .build()
        );
        mockMvc.perform(get("/example/{id}", entity.getId()))
                .andDo(print())
                .andExpect(status().isOk());
    }


}