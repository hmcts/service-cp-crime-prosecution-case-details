package uk.gov.hmcts.cp.mappers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Defendant;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.Offence;
import uk.gov.hmcts.cp.domain.ProgressionResponse.ProsecutionCase.ReportingRestriction;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@ExtendWith(MockitoExtension.class)
class CaseDetailMapperTest {

    private final CaseDetailMapper caseDetailMapper = new CaseDetailMapper();

    @Test
    void null_progression_response_should_return_response() {
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(null);
        assertThat(response.getCaseStatus()).isNull();
        assertThat(response.getReportingRestrictions()).isFalse();
    }

    @Test
    void null_progression_case_should_return_response() {
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertThat(response.getCaseStatus()).isNull();
        assertThat(response.getReportingRestrictions()).isFalse();
    }

    @Test
    void empty_defendants_should_return_response() {
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertThat(response.getCaseStatus()).isEqualTo("case-study-status");
        assertThat(response.getReportingRestrictions()).isFalse();
    }

    @Test
    void empty_offenses_should_return_response() {
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .defendants(List.of(Defendant.builder().build()))
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertThat(response.getCaseStatus()).isEqualTo("case-study-status");
        assertThat(response.getReportingRestrictions()).isFalse();
    }

    @Test
    void empty_reporting_restrictions_should_return_response() {
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .defendants(List.of(Defendant.builder()
                                .offences(List.of(Offence.builder().build()))
                                .build()))
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertThat(response.getCaseStatus()).isEqualTo("case-study-status");
        assertThat(response.getReportingRestrictions()).isFalse();
    }

    @Test
    void valid_reporting_restrictions_should_return_response() {
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .defendants(List.of(Defendant.builder()
                                .offences(List.of(Offence.builder()
                                        .reportingRestrictions(List.of(ReportingRestriction.builder().build()))
                                        .build()))
                                .build()))
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertThat(response.getCaseStatus()).isEqualTo("case-study-status");
        assertThat(response.getReportingRestrictions()).isTrue();
    }
}