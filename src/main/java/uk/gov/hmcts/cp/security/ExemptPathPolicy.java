package uk.gov.hmcts.cp.security;

import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ExemptPathPolicy {

    private static final Set<String> EXEMPT_PATHS = Set.of(
        "/",
        "/actuator/health",
        "/actuator/health/liveness",
        "/actuator/health/readiness",
        "/actuator/info",
        "/actuator/prometheus"
    );

    public Set<String> exemptPaths() {
        return EXEMPT_PATHS;
    }

    public boolean isExempt(final String path) {
        return EXEMPT_PATHS.contains(path);
    }
}
