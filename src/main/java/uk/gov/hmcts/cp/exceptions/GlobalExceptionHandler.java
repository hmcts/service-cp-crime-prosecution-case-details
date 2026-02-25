package uk.gov.hmcts.cp.exceptions;

import io.micrometer.tracing.Tracer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uk.gov.hmcts.cp.openapi.model.ErrorResponse;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@AllArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Tracer tracer;

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(final ResponseStatusException responseStatusException) {
        log.error("GlobalExceptionHandler handleResponseStatusException", responseStatusException);
        final String errorMessage = responseStatusException.getReason() != null
                ? responseStatusException.getReason()
                : responseStatusException.getMessage();
        return ResponseEntity
                .status(responseStatusException.getStatusCode())
                .body(buildErrorResponse(errorMessage));
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerException(final HttpServerErrorException e) {
        log.error("GlobalExceptionHandler handleServerException", e);
        return ResponseEntity
                .status(e.getStatusCode())
                .body(buildErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleClientException(final HttpClientErrorException e) {
        log.error("GlobalExceptionHandler handleClientException", e);
        return ResponseEntity
                .status(e.getStatusCode())
                .body(buildErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(final NoResourceFoundException e) {
        log.error("GlobalExceptionHandler handleNoResourceFoundException");
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(final NoHandlerFoundException e) {
        log.error("GlobalExceptionHandler handleNoHandlerFoundException");
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(final Exception e) {
        log.error("GlobalExceptionHandler handleException", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(e.getMessage()));
    }

    private ErrorResponse buildErrorResponse(final String message) {
        return ErrorResponse.builder()
                .message(message)
                .timestamp(Instant.now())
                .traceId(Objects.requireNonNull(tracer.currentSpan()).context().traceId())
                .build();
    }
}
