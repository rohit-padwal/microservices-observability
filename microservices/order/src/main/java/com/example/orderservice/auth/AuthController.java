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

@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.username(), request.password());
        return new LoginResponse(result.accessToken(), "Bearer", result.expiresAt(),
                result.username(), List.of(result.role()));
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public AuthService.UserSummary createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return authService.createOperator(request.username(), request.password());
    }

    @GetMapping("/me")
    public CurrentUser currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new CurrentUser(jwt.getSubject(), jwt.getClaimAsStringList("roles"), jwt.getClaim("userId"));
    }

    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank String password) {}

    public record CreateOperatorRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                        @NotBlank @Size(min = 12, max = 72) String password) {}

    public record LoginResponse(String accessToken, String tokenType, java.time.Instant expiresAt,
                                String username, List<UserRole> roles) {}

    public record CurrentUser(String username, List<String> roles, Object userId) {}
}