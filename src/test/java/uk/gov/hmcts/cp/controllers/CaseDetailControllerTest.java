package uk.gov.hmcts.cp.controllers;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;
import uk.gov.hmcts.cp.services.CaseDetailService;
import uk.gov.hmcts.cp.services.CaseUrnMapperService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@Slf4j
@ExtendWith(MockitoExtension.class)
class CaseDetailControllerTest {

    private static final String ACTIVE_STATUS = "ACTIVE";

    @Mock
    private CaseDetailService caseDetailService;

    @Mock
    private CaseUrnMapperService caseUrnMapperService;

    @InjectMocks
    private CaseDetailController caseDetailController;

    @Test
    void getCaseDetails_ShouldReturnWithOkStatus() {
        UUID caseUrn = UUID.randomUUID();
        UUID caseId = UUID.randomUUID();

        CaseDetailResponse mockResponse = CaseDetailResponse.builder()
                .caseStatus(ACTIVE_STATUS)
                .reportingRestrictions(true)
                .build();

        when(caseUrnMapperService.getCaseId(anyString())).thenReturn(caseId);
        when(caseDetailService.getCaseDetailsByCaseId(caseId)).thenReturn(mockResponse);

        ResponseEntity<?> response = caseDetailController.getCaseDetailsByCaseUrn(caseUrn.toString());

        assertNotNull(response);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        CaseDetailResponse responseBody = (CaseDetailResponse) response.getBody();
        assertThat(responseBody.getCaseStatus()).isEqualTo(ACTIVE_STATUS);
        assertThat(responseBody.getReportingRestrictions()).isTrue();
    }

    @Test
    void getCaseDetailsByCaseUrn_ShouldSanitizeCaseUrn() {
        String unsanitizedCaseUrn = "<script>alert('xss')</script>";
        UUID caseId = UUID.randomUUID();
        CaseDetailResponse mockResponse = CaseDetailResponse.builder()
                .build();

        lenient().when(caseUrnMapperService.getCaseId(anyString())).thenReturn(caseId);
        lenient().when(caseDetailService.getCaseDetailsByCaseId(caseId)).thenReturn(mockResponse);

        ResponseEntity<?> response = caseDetailController.getCaseDetailsByCaseUrn(unsanitizedCaseUrn);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}