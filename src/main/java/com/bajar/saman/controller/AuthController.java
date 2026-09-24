package com.bajar.saman.controller;

import com.bajar.saman.dto.AuthResponse;
import com.bajar.saman.dto.LoginRequest;
import com.bajar.saman.dto.RegisterRequest;
import com.bajar.saman.dto.RefreshTokenRequest;
import com.bajar.saman.entity.User;
import com.bajar.saman.repository.UserRoleRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.bajar.saman.service.AuthenticationService;
import com.bajar.saman.service.RefreshSessionService;
import com.bajar.saman.service.UserRegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final RefreshSessionService refreshSessionService;
    private final UserRoleRepository userRoleRepository;

    public AuthController(
            UserRegistrationService registrationService,
            AuthenticationService authenticationService,
            RefreshSessionService refreshSessionService,
            UserRoleRepository userRoleRepository) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.refreshSessionService = refreshSessionService;
        this.userRoleRepository = userRoleRepository;
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
        AuthResponse response = refreshSessionService.issue(user);

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

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(refreshSessionService.rotate(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshSessionService.revoke(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Roadmap Addendum v2 §3: the frontend needs a way to know the logged-in
     * user's roles (JWT deliberately doesn't carry them — see JwtService's
     * own comment on why roles are always re-fetched from the DB, not
     * embedded in the token). This endpoint is that source: the frontend
     * calls it once after login/on app load to know what to show/hide.
     */
    @GetMapping("/me")
    public ResponseEntity<com.bajar.saman.dto.UserProfileResponse> me(@AuthenticationPrincipal User user) {
        // Now uses the field injected via the constructor above — no more
        // per-method @Autowired parameter (that pattern doesn't work
        // cleanly alongside @AuthenticationPrincipal, as the earlier error
        // showed: Spring tried to bind UserRoleRepository as a request-body
        // form object instead of resolving it as a dependency).
        java.util.List<String> roles = userRoleRepository.findRoleNamesByUserId(user.getId());
        return ResponseEntity.ok(new com.bajar.saman.dto.UserProfileResponse(
                user.getId(), user.getEmail(), roles));
    }
}
