package uk.gov.hmcts.cp.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.hmcts.cp.entities.ExampleEntity;
import uk.gov.hmcts.cp.openapi.model.ExampleResponse;

@FunctionalInterface
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExampleMapper {

    @Mapping(source = "id", target = "exampleId")
    ExampleResponse mapExampleToResponse(ExampleEntity exampleEntity);
}
