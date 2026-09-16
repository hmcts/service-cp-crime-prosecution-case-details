package uk.gov.hmcts.cp.filters.auth;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.owasp.encoder.Encode;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.hmcts.cp.config.AuthProperties;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;
import uk.gov.hmcts.cp.security.AuthMode;
import uk.gov.hmcts.cp.security.CallerIdentity;
import uk.gov.hmcts.cp.security.EntraTokenValidator;
import uk.gov.hmcts.cp.security.ExemptPathPolicy;
import uk.gov.hmcts.cp.security.TokenRejectionReason;
import uk.gov.hmcts.cp.security.TokenValidationException;

import java.io.IOException;
import java.time.Instant;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
@Slf4j
public class EntraAuthenticationFilter extends OncePerRequestFilter {

    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_VERIFIED = "clientVerified";

    private static final String SUCCESS_METRIC = "auth.token.validation.success";
    private static final String FAILURE_METRIC = "auth.token.validation.failure";
    private static final String OBSERVED_METRIC = "auth.token.validation.observed";
    private static final String REASON_TAG = "reason";

    private final AuthProperties authProperties;
    private final EntraTokenValidator tokenValidator;
    private final ExemptPathPolicy exemptPathPolicy;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final JsonMapper jsonMapper;

    public EntraAuthenticationFilter(final AuthProperties authProperties,
                                     final EntraTokenValidator tokenValidator,
                                     final ExemptPathPolicy exemptPathPolicy,
                                     final MeterRegistry meterRegistry,
                                     final Tracer tracer,
                                     final JsonMapper jsonMapper) {
        this.authProperties = authProperties;
        this.tokenValidator = tokenValidator;
        this.exemptPathPolicy = exemptPathPolicy;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.jsonMapper = jsonMapper;
    }

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return authProperties.getMode() == AuthMode.OFF || exemptPathPolicy.isExempt(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain filterChain) throws ServletException, IOException {
        try {
            final CallerIdentity caller = tokenValidator.validate(request.getHeader(HttpHeaders.AUTHORIZATION));
            meterRegistry.counter(SUCCESS_METRIC).increment();
            MDC.put(CLIENT_ID, caller.clientId());
            MDC.put(CLIENT_VERIFIED, String.valueOf(caller.verified()));
            filterChain.doFilter(request, response);
        } catch (TokenValidationException e) {
            handleRejection(request, response, filterChain, e.getReason());
        } finally {
            MDC.remove(CLIENT_ID);
            MDC.remove(CLIENT_VERIFIED);
        }
    }

    private void handleRejection(final HttpServletRequest request,
                                 final HttpServletResponse response,
                                 final FilterChain filterChain,
                                 final TokenRejectionReason reason) throws ServletException, IOException {
        // Method and path come from the request, so they are encoded before they reach the log.
        log.warn("Token validation failed: reason={} method={} path={}",
            reason, Encode.forJava(request.getMethod()), Encode.forJava(request.getRequestURI()));
        if (authProperties.getMode() == AuthMode.OBSERVE) {
            counter(OBSERVED_METRIC, reason).increment();
            MDC.put(CLIENT_VERIFIED, String.valueOf(tokenValidator.unverifiedIdentity().verified()));
            filterChain.doFilter(request, response);
        } else {
            counter(FAILURE_METRIC, reason).increment();
            writeRejection(response, reason);
        }
    }

    private void writeRejection(final HttpServletResponse response, final TokenRejectionReason reason) throws IOException {
        response.setStatus(reason.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
            String.format("Bearer error=\"%s\", error_description=\"%s\"", reason.getErrorCode(), reason.name()));
        response.getWriter().write(jsonMapper.writeValueAsString(errorResponse(reason)));
    }

    private ErrorResponse errorResponse(final TokenRejectionReason reason) {
        final Span span = tracer.currentSpan();
        return ErrorResponse.builder()
            .message(reason.name())
            .timestamp(Instant.now())
            .traceId(span == null ? null : span.context().traceId())
            .build();
    }

    private Counter counter(final String name, final TokenRejectionReason reason) {
        return Counter.builder(name).tag(REASON_TAG, reason.name()).register(meterRegistry);
    }
}
