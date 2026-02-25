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

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class CaseDetailMapperTest {

    private final CaseDetailMapper caseDetailMapper = new CaseDetailMapper();

    @Test
    void mapToCaseDetailResponse_forNullResponse() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus(null)
                .reportingRestrictions(false)
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(null);
        assertEquals(expectedResponse, response);
    }

    @Test
    void mapToCaseDetailResponse_forNullProsecutionCaseResponse() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus(null)
                .reportingRestrictions(false)
                .build();
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertEquals(expectedResponse, response);
    }

    @Test
    void mapToCaseDetailResponse_withoutDefendants() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus("case-study-status")
                .reportingRestrictions(false)
                .build();
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertEquals(expectedResponse, response);
    }

    @Test
    void mapToCaseDetailResponse_withoutOffenses() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus("case-study-status")
                .reportingRestrictions(false)
                .build();
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .defendants(List.of(Defendant.builder().build()))
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertEquals(expectedResponse, response);
    }

    @Test
    void mapToCaseDetailResponse_withoutReportingRestrictions() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus("case-study-status")
                .reportingRestrictions(false)
                .build();
        ProgressionResponse progressionResponse = ProgressionResponse.builder()
                .prosecutionCase(ProsecutionCase.builder()
                        .caseStatus("case-study-status")
                        .defendants(List.of(Defendant.builder()
                                .offences(List.of(Offence.builder().build()))
                                .build()))
                        .build())
                .build();
        CaseDetailResponse response = caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
        assertEquals(expectedResponse, response);
    }

    @Test
    void mapToCaseDetailResponse_withReportingRestrictions() {
        CaseDetailResponse expectedResponse = CaseDetailResponse.builder()
                .caseStatus("case-study-status")
                .reportingRestrictions(true)
                .build();
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
        assertEquals(expectedResponse, response);
    }
}