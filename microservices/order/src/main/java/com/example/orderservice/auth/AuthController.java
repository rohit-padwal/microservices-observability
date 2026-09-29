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

    /** Returns a signed short-lived access token and role summary; the password is never echoed or serialized. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresAt(),
                result.username(), List.of(result.role()));
    }

    /** Creates an OPERATOR, not an ADMIN, so API requests cannot self-elevate privileges. */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthService.UserSummary createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return authService.createOperator(request.username(), request.password());
    }

    @GetMapping("/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsStringList("roles"), jwt.getClaim("userId"));
    }

    /** Login body; password policy is enforced when creating credentials, while login verifies the existing hash. */
    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank String password) {}

    public record CreateOperatorRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                        @NotBlank @Size(min = 12, max = 72) String password) {}

    /** Safe response fields only: never include the submitted password or persisted password hash. */
    public record LoginResponse(String accessToken, String tokenType, java.time.Instant expiresAt,
                                String username, List<UserRole> roles) {}

    public record CurrentUser(String username, List<String> roles, Object userId) {}
}