package uk.gov.hmcts.cp.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

@Component
public class CaseDetailMapper {

    public CaseDetailResponse mapToCaseDetailResponse(final ProgressionResponse progressionResponse) {
        final Optional<ProgressionResponse.ProsecutionCase> prosecutionCase = Optional.ofNullable(progressionResponse)
                .map(ProgressionResponse::getProsecutionCase);

        final String caseStatus = prosecutionCase
                .map(ProgressionResponse.ProsecutionCase::getCaseStatus)
                .orElse(null);

        final Boolean reportingRestrictions = Optional.ofNullable(progressionResponse)
                .map(ProgressionResponse::getProsecutionCase)
                .map(ProgressionResponse.ProsecutionCase::getDefendants)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .flatMap(defendant -> Optional.ofNullable(defendant.getOffences())
                        .orElse(Collections.emptyList())
                        .stream())
                .filter(Objects::nonNull)
                .flatMap(offence -> Optional.ofNullable(offence.getReportingRestrictions())
                        .orElse(Collections.emptyList())
                        .stream())
                .findAny()
                .isPresent();

        return CaseDetailResponse.builder()
                .caseStatus(caseStatus)
                .reportingRestrictions(reportingRestrictions)
                .build();
    }
}
