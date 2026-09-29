package com.example.orderservice.exception;

import com.example.orderservice.auth.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(body(401, "Unauthorized", "Invalid username or password"));
    }

    @ExceptionHandler(AuthService.DuplicateUsernameException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateUsername(AuthService.DuplicateUsernameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body(409, "Conflict", "Username is already provisioned"));
    }

    @ExceptionHandler(AuthService.WeakPasswordException.class)
    public ResponseEntity<Map<String, Object>> handleWeakPassword(AuthService.WeakPasswordException ex) {
        return ResponseEntity.badRequest().body(body(400, "Bad Request", "Password must be at least 12 characters and at most 72 UTF-8 bytes"));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(OrderNotFoundException ex) {
        log.warn("Order not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(404, "Not Found", ex.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        // Return a stable, sanitized 400 payload without exposing framework or binding internals.
        return ResponseEntity.badRequest().body(body(400, "Bad Request", "Request parameters or body are invalid"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body(500, "Internal Server Error", "An unexpected error occurred"));
    }

    private Map<String, Object> body(int status, String error, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", error,
                "message", message
        );
    }
}
