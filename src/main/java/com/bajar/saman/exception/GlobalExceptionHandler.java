package com.bajar.saman.exception;

import com.bajar.saman.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * ONE place that catches exceptions thrown anywhere in the controller layer and
 * converts them into our standard ErrorResponse JSON shape (defined in the dto
 * package) with the correct HTTP status code. Without this, an unhandled exception
 * would bubble up as Spring's default error page/JSON, which can leak internal
 * details (stack traces, class names) to the client — a real information-disclosure
 * risk in production. This is the "global exception handling" piece from the
 * roadmap doc's Foundation module (Section 9, item 1), arriving a bit later than
 * planned there because we needed real exceptions to actually handle first.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // One Logger per class is the standard SLF4J convention — getLogger(Class) ties
    // every log line from this class to its exact source, so log output (or a log
    // aggregation tool later) can be filtered/searched by class name.
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Registration attempted with an email that already exists -> 409 Conflict
    // (the HTTP-correct status for "this resource already exists").
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmail(
            DuplicateEmailException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // Wrong email/password on login -> 401 Unauthorized.
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // Triggered automatically when a @Valid-annotated request body fails its
    // @NotBlank / @Email / @Size checks (from RegisterRequest/LoginRequest) -> 400.
    // We collect ALL field errors into one readable string rather than just the
    // first one, so the client sees every problem at once instead of fixing them
    // one at a time across multiple round trips.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        String combinedMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return buildResponse(HttpStatus.BAD_REQUEST, combinedMessage, request);
    }

    // Catch-all safety net for anything we didn't anticipate. Deliberately generic
    // message ("An unexpected error occurred") — we never leak ex.getMessage() or
    // the exception's class name here, because an unanticipated exception might
    // contain internal details (SQL fragments, file paths, etc.) we don't want a
    // client to ever see. Full details still go to the server-side console/logs
    // via ex.printStackTrace() equivalent (we'll wire up proper logging later —
    // flagging this as a near-term follow-up too).
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        // log.error(message, throwable) — passing the exception object itself (not
        // just ex.getMessage()) makes Logback print the FULL stack trace to the
        // server console/log file. This is exactly the detail we deliberately keep
        // OUT of the client-facing response (see this method's own reasoning below)
        // but need SOMEWHERE for us to actually debug it. Without this line, an
        // unexpected error was previously invisible outside a live debugger session.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    // Shared helper — every handler above builds the same ErrorResponse shape, just
    // with different status/message. Extracted here to avoid repeating this
    // construction logic five times (DRY principle).
    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, HttpServletRequest request) {

        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        return ResponseEntity.status(status).body(body);
    }
}