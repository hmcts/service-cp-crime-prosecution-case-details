package uk.gov.hmcts.cp.controllers;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.cp.filters.jwt.AuthDetails;
import uk.gov.hmcts.cp.openapi.api.RootApi;

@RestController
@AllArgsConstructor
@Slf4j
public class RootController implements RootApi {

    private AuthDetails jwtToken;

    @Override
    public ResponseEntity<String> getRoot() {
        log.info("START");
        return ResponseEntity.ok("Hello " + jwtToken.getUserName());
    }
}
