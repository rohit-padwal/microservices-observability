package com.example.orderservice.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** HTTP boundary for public login, authenticated identity lookup, and ADMIN-only operator provisioning. */
@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticates the submitted credentials and returns a signed access token.
     * Invalid credentials are mapped to a generic 401 by the global advice.
     *
     * @param request validated username/password body
     * @return bearer token, expiry, and non-secret account/role metadata
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresAt(),
                result.username(), List.of(result.role()));
    }

    /**
     * Provisions an OPERATOR account; method security requires ADMIN and the role is never caller-selectable.
     *
     * @param request validated username and initial password
     * @return newly created account summary without its BCrypt hash
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthService.UserSummary createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return authService.createOperator(request.username(), request.password());
    }

    /** @return identity claims already authenticated by the resource-server filter */
    @GetMapping("/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsStringList("roles"), jwt.getClaim("userId"));
    }

    /** Login body; password policy is enforced when creating credentials, while login verifies the existing hash. */
    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank String password) {}

    /** Admin provisioning payload; password is hashed before persistence and is never returned. */
    public record CreateOperatorRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                        @NotBlank @Size(min = 12, max = 72) String password) {}

    /** Safe response fields only: never include the submitted password or persisted password hash. */
    public record LoginResponse(String accessToken, String tokenType, java.time.Instant expiresAt,
                                String username, List<UserRole> roles) {}

    /** Authenticated token identity, returned for client display/role-aware navigation only. */
    public record CurrentUser(String username, List<String> roles, Object userId) {}
}