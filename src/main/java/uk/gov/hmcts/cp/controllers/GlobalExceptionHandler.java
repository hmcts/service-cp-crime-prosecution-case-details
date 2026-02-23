package uk.gov.hmcts.cp.controllers;

import io.micrometer.tracing.Tracer;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Tracer tracer;

    public GlobalExceptionHandler(final Tracer tracer) {
        this.tracer = tracer;
    }

}
