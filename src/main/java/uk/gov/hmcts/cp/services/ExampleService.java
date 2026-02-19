package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.mappers.ExampleMapper;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;
import uk.gov.hmcts.cp.repositories.ExampleRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleService {

    private final ExampleRepository exampleRepository;
    private final ExampleMapper exampleMapper;

    public ExampleResponse getExampleById(final long exampleId) {
        final ExampleEntity entity = exampleRepository.findById(exampleId).orElseThrow();
        return exampleMapper.mapExampleToResponse(entity);
    }

}
