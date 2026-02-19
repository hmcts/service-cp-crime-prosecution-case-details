package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;

import static org.assertj.core.api.Assertions.assertThat;

class ExampleMapperTest {

    ExampleMapper exampleMapper = new ExampleMapperImpl();

    @Test
    void map_to_response_should_map_all_fields() {
        ExampleEntity exampleEntity = ExampleEntity.builder().id(20L).exampleText("Hello").build();
        ExampleResponse response = exampleMapper.mapExampleToResponse(exampleEntity);

        assertThat(response.getExampleId()).isEqualTo(20L);
        assertThat(response.getExampleText()).isEqualTo("Hello");
    }
}