package de.ragtractusx.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import de.ragtractusx.config.LlmConfig.LlmUnavailableException;
import de.ragtractusx.ingest.IngestionService.IngestionInProgressException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import jakarta.validation.ConstraintViolationException;

/**
 * Maps failures to RFC 9457 problem responses. Upstream (Claude/OpenAI) problems become 429/502/503/504 so clients can
 * tell "retry later" apart from "your request is wrong". Validation errors are handled by Spring's defaults (400).
 */
@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalid(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(LlmUnavailableException.class)
    ProblemDetail unavailable(LlmUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(IngestionInProgressException.class)
    ProblemDetail conflict(IngestionInProgressException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(RateLimitException.class)
    ResponseEntity<ProblemDetail> rateLimited(RateLimitException e) {
        log.warn("Upstream rate limit: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Upstream model is rate limited, retry later"));
    }

    @ExceptionHandler(TimeoutException.class)
    ProblemDetail timeout(TimeoutException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, "Upstream model timed out");
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail upstreamAuth(AuthenticationException e) {
        log.error("Upstream authentication failed - check API keys: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Upstream model is not available");
    }

    @ExceptionHandler(HttpException.class)
    ProblemDetail upstream(HttpException e) {
        log.warn("Upstream HTTP {}: {}", e.statusCode(), e.getMessage());
        HttpStatus status = e.statusCode() == 529 || e.statusCode() == 503 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ProblemDetail.forStatusAndDetail(status, "Upstream model error (" + e.statusCode() + ")");
    }
}
