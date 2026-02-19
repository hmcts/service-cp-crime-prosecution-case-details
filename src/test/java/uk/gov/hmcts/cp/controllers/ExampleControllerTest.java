package uk.gov.hmcts.cp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;
import uk.gov.hmcts.cp.services.ExampleService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Slf4j
class ExampleControllerTest {
    @Mock
    ExampleService exampleService;

    @InjectMocks
    private ExampleController exampleController;

    @Test
    void get_example_by_id_should_call_service() {
        ExampleResponse exampleResponse = ExampleResponse.builder().build();
        when(exampleService.getExampleById(9L)).thenReturn(exampleResponse);

        ResponseEntity<?> response = exampleController.getExampleByExampleId(9L);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(exampleResponse);
    }
} 