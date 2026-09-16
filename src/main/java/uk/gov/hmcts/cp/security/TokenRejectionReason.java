package uk.gov.hmcts.cp.security;

import org.springframework.http.HttpStatus;

public enum TokenRejectionReason {

    MISSING_AUTHORIZATION_HEADER(HttpStatus.UNAUTHORIZED),
    UNSUPPORTED_SCHEME(HttpStatus.UNAUTHORIZED),
    MALFORMED_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED),
    INVALID_AUDIENCE(HttpStatus.UNAUTHORIZED),
    INVALID_ISSUER(HttpStatus.UNAUTHORIZED),
    INVALID_TENANT(HttpStatus.UNAUTHORIZED),
    UNSUPPORTED_TOKEN_VERSION(HttpStatus.UNAUTHORIZED),
    MISSING_EXPIRY(HttpStatus.UNAUTHORIZED),
    EXPIRED(HttpStatus.UNAUTHORIZED),
    NOT_YET_VALID(HttpStatus.UNAUTHORIZED),
    DELEGATED_TOKEN(HttpStatus.UNAUTHORIZED),
    INVALID_CLIENT_ID(HttpStatus.UNAUTHORIZED),
    MISSING_ROLE(HttpStatus.FORBIDDEN);

    private static final String ERROR_INVALID_TOKEN = "invalid_token";
    private static final String ERROR_INSUFFICIENT_SCOPE = "insufficient_scope";

    private final HttpStatus status;

    TokenRejectionReason(final HttpStatus status) {
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return status == HttpStatus.FORBIDDEN ? ERROR_INSUFFICIENT_SCOPE : ERROR_INVALID_TOKEN;
    }
}
