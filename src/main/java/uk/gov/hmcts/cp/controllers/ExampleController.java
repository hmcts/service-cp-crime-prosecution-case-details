package uk.gov.hmcts.cp.controllers;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.openapi.api.ExamplesApi;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;
import uk.gov.hmcts.cp.services.ExampleService;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ExampleController implements ExamplesApi {

    private final ExampleService exampleService;

    @Override
    public ResponseEntity<ExampleResponse> getExampleByExampleId(@NotNull final Long exampleId) {

        log.info("getExampleByExampleId example for {}", exampleId);
        return ResponseEntity.ok(exampleService.getExampleById(exampleId));
    }

}
