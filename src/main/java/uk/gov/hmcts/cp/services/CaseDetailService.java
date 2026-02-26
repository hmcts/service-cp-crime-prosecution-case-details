package uk.gov.hmcts.cp.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.cp.clients.ProgressionClient;
import uk.gov.hmcts.cp.domain.ProgressionResponse;
import uk.gov.hmcts.cp.mappers.CaseDetailMapper;
import uk.gov.hmcts.cp.openapi.model.CaseDetailResponse;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaseDetailService {

    private final ProgressionClient progressionClient;
    private final CaseDetailMapper caseDetailMapper;

    public CaseDetailResponse getCaseDetailsByCaseId(final UUID caseId) throws ResponseStatusException {
        validateOrThrowError(caseId, HttpStatus.BAD_REQUEST, "caseId is mandatory");
        final ProgressionResponse progressionResponse = progressionClient.getProgressionResponse(caseId);
        validateOrThrowError(progressionResponse.getProsecutionCase(), HttpStatus.NOT_FOUND, "progression response should not be empty");
        return caseDetailMapper.mapToCaseDetailResponse(progressionResponse);
    }

    private void validateOrThrowError(final Object obj, final HttpStatus status, final String errorMessage) {
        if (ObjectUtils.isEmpty(obj)) {
            log.error(errorMessage);
            throw new ResponseStatusException(status, errorMessage);
        }
    }
}
