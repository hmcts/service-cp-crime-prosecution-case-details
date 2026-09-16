package uk.gov.hmcts.cp.security;

import lombok.Getter;

@Getter
public class TokenValidationException extends Exception {

    private static final long serialVersionUID = 1L;

    private final TokenRejectionReason reason;

    public TokenValidationException(final TokenRejectionReason reason) {
        super(reason.name());
        this.reason = reason;
    }
}
