package uk.gov.hmcts.cp.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.mappers.ExampleMapper;
import uk.gov.hmcts.cp.mappers.ExampleMapperImpl;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;
import uk.gov.hmcts.cp.repositories.ExampleRepository;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExampleServiceTest {
    @Mock
    ExampleRepository exampleRepository;
    @Spy
    ExampleMapper mapper = new ExampleMapperImpl();

    @InjectMocks
    ExampleService exampleService;

    @Test
    void service_should_return_mapped_response() {
        ExampleEntity exampleEntity = ExampleEntity.builder().id(123L).exampleText("Text").build();
        when(exampleRepository.findById(123L)).thenReturn(Optional.ofNullable(exampleEntity));
        ExampleResponse response = exampleService.getExampleById(123L);
        assertThat(response.getExampleId()).isEqualTo(123L);
        assertThat(response.getExampleText()).isEqualTo("Text");
    }
}