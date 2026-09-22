package com.bajar.saman.exception;

import com.bajar.saman.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void malformedUuidReturnsBadRequestWithoutLeakingConversionDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/payments/initiate");

        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException(
                        "not-a-uuid", UUID.class, "Idempotency-Key", null,
                        new IllegalArgumentException("internal conversion detail"));

        ResponseEntity<ErrorResponse> response =
                handler.handleTypeMismatch(exception, request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Invalid value for parameter: Idempotency-Key",
                response.getBody().message());
        assertEquals("/api/payments/initiate", response.getBody().path());
    }
}
