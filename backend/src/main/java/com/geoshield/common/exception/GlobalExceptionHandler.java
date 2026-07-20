package com.geoshield.common.exception;

import com.geoshield.common.api.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) { return error(HttpStatus.NOT_FOUND, exception.getMessage(), "RESOURCE_NOT_FOUND"); }
    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException exception) { return error(HttpStatus.CONFLICT, exception.getMessage(), "CONFLICT"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return error(HttpStatus.CONFLICT, "A record with the supplied unique value already exists", "CONFLICT");
    }
    @ExceptionHandler(InvalidStateTransitionException.class)
    ResponseEntity<ApiError> handleInvalidState(InvalidStateTransitionException exception) { return error(HttpStatus.CONFLICT, exception.getMessage(), "INVALID_STATE_TRANSITION"); }
    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception) { return error(HttpStatus.UNAUTHORIZED, exception.getMessage(), "UNAUTHORIZED"); }
    @ExceptionHandler(AccountLockedException.class)
    ResponseEntity<ApiError> handleAccountLocked(AccountLockedException exception) { return error(HttpStatus.LOCKED, exception.getMessage(), "ACCOUNT_LOCKED"); }
    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException exception) { return error(HttpStatus.FORBIDDEN, exception.getMessage(), "FORBIDDEN"); }
    @ExceptionHandler(ValidationException.class)
    ResponseEntity<ApiError> handleValidation(ValidationException exception) { return error(HttpStatus.BAD_REQUEST, exception.getMessage(), "VALIDATION_ERROR"); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Validation failed");
        return error(HttpStatus.BAD_REQUEST, message, "VALIDATION_ERROR");
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String message, String code) { return ResponseEntity.status(status).body(ApiError.of(message, code)); }
}
