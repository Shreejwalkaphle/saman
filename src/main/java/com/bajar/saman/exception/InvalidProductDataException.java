package com.bajar.saman.exception;

/**
 * Thrown for business-rule violations on product data that aren't structural
 * enough to be @Valid/DTO-level validation (e.g. negative price) — this exception
 * covers cases specific to product business logic, kept separate from a generic
 * validation exception so GlobalExceptionHandler can give it a distinct, clear
 * message.
 */
public class InvalidProductDataException extends RuntimeException {
    public InvalidProductDataException(String message) {
        super(message);
    }
}