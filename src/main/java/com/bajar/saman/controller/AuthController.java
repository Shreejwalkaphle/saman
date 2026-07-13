package com.bajar.saman.controller;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.dto.LoginRequest;
import com.bajar.saman.dto.RegisterRequest;
import com.bajar.saman.entity.User;
import com.bajar.saman.security.JwtService;
import com.bajar.saman.service.AuthenticationService;
import com.bajar.saman.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The ONLY layer that talks HTTP. Notice this class contains NO business logic —
 * no password hashing, no role assignment, no token-signing details. It just:
 *   1. Receives a validated DTO (validation already ran before this method executes)
 *   2. Calls the appropriate service method
 *   3. Wraps the service's return value in the right DTO + HTTP status
 * This is the Single Responsibility Principle in practice (roadmap doc Section 4) —
 * this class's only job is "translate between HTTP and the service layer."
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final JwtService jwtService;

    public AuthController(
            UserRegistrationService registrationService,
            AuthenticationService authenticationService,
            JwtService jwtService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.jwtService = jwtService;
    }

    /**
     * POST /api/auth/register
     *
     * @Valid triggers the RegisterRequest field validations (@NotBlank, @Email,
     * @Size). If any fail, Spring throws MethodArgumentNotValidException BEFORE this
     * method body even runs — GlobalExceptionHandler catches it and returns 400.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {

        User user = registrationService.register(request.email(), request.password());

        // Registration doesn't currently issue a token via AuthenticationService
        // (that would mean re-verifying a password we just set, which is redundant) —
        // instead we generate the token directly here, the same way login does
        // internally, so a newly registered user is immediately logged in without a
        // separate login call.
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        AuthResponse response = new AuthResponse(token, user.getId(), user.getEmail());

        // 201 Created is the HTTP-correct status for "a new resource was created" —
        // NOT 200 OK, which implies an existing resource was just read/returned.
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authenticationService.login(request.email(), request.password());
        return ResponseEntity.ok(response);
    }
}