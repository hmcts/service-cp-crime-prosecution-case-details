package uk.gov.hmcts.cp.controllers;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.owasp.encoder.Encode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.openapi.api.CaseDetailsApi;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;
import uk.gov.hmcts.cp.services.CaseDetailService;
import uk.gov.hmcts.cp.services.CaseUrnMapperService;

import java.util.UUID;

@RestController
@Slf4j
public class CaseDetailController implements CaseDetailsApi {
    private final CaseDetailService caseDetailService;
    private final CaseUrnMapperService caseUrnMapperService;

    public CaseDetailController(final CaseDetailService caseDetailService,
                                   final CaseUrnMapperService caseUrnMapperService) {
        this.caseDetailService = caseDetailService;
        this.caseUrnMapperService = caseUrnMapperService;
    }

    @Override
    @NonNull
    public ResponseEntity<CaseDetailResponse> getCaseDetailsByCaseUrn(final String caseUrn) {
        try {
            final String sanitizedCaseUrn = Encode.forJava(caseUrn);
            log.info("Received request to get case details for caseUrn:{}", sanitizedCaseUrn);
            final UUID caseId = caseUrnMapperService.getCaseId(sanitizedCaseUrn);
            final CaseDetailResponse caseDetailResponse = caseDetailService.getCaseDetailsByCaseId(caseId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(caseDetailResponse);
        } catch (ResponseStatusException e) {
            log.error(e.getMessage());
            throw e;
        }
    }
}
