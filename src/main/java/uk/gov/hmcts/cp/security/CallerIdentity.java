package uk.gov.hmcts.cp.security;

public record CallerIdentity(String clientId, boolean verified) {
}
