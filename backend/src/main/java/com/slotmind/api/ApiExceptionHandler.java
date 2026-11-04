package com.slotmind.api;

import com.slotmind.domain.DomainException;
import com.slotmind.domain.NotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Maps a rejected business rule to 409. */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorBody> domain(DomainException exception) {
        log.info("Rejected request {}: {}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody(exception.getCode(), exception.getMessage()));
    }

    /** Maps a lost optimistic lock to 409 so the desk can refresh and retry. */
    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErrorBody> conflict(Exception exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorBody("CONFLICT", "The slot was just taken. Refresh and try again."));
    }

    /** Maps a missing entity to 404. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> missing(NotFoundException exception) {
        log.info("Not found {}: {}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorBody(exception.getCode(), exception.getMessage()));
    }

    /** Maps an unexpected failure to 500 without exposing the cause. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorBody> unexpected(Exception exception) {
        log.error("Request failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(internalError());
    }

    /** Maps the first invalid field to a 400. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " is required")
                .orElse("Request is invalid");
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID", message));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Request failed", exception);
            body = internalError();
        }
        return super.handleExceptionInternal(exception, body, headers, status, request);
    }

    private static ErrorBody internalError() {
        return new ErrorBody("INTERNAL", "The request could not be completed.");
    }
}
