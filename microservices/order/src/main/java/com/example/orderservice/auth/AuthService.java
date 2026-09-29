package com.example.orderservice.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Owns credential verification and token issuance; controllers stay focused on HTTP input/output contracts. */
@Service
public class AuthService {

    public static final String ISSUER = "fieldnotes-observability";
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);

    private final AppUserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;

    public AuthService(AppUserRepository userRepository,
                       AuthenticationManager authenticationManager,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Clock clock,
                       @Value("${security.jwt.issuer:fieldnotes-observability}") String issuer) {
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.issuer = issuer;
    }

    /** Spring verifies the BCrypt hash first; the token then carries only subject, user ID, role, issuer, and expiry claims. */
    public LoginResult login(String username, String password) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username.trim().toLowerCase(), password));
            AppUser user = (AppUser) authentication.getPrincipal();
            Instant issuedAt = clock.instant();
            Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_TTL);
            JwtClaimsSet claims = JwtClaimsSet.builder()
                    .issuer(issuer)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .subject(user.getUsername())
                    .claim("roles", List.of(user.getRole().name()))
                    .claim("userId", user.getId())
                    .build();
            String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                    JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
            return new LoginResult(accessToken, expiresAt, user.getUsername(), user.getRole());
        } catch (BadCredentialsException ex) {
            throw ex;
        } catch (AuthenticationException ex) {
            throw new AuthenticationServiceException("Authentication could not be completed", ex);
        }
    }

    /** New accounts are operators by default; only the ADMIN-protected controller endpoint can reach this provisioning path. */
    @Transactional
    public UserSummary createOperator(String username, String password) {
        String normalizedUsername = username.trim().toLowerCase();
        validatePassword(password);
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new DuplicateUsernameException();
        }
        AppUser user = userRepository.save(new AppUser(normalizedUsername,
                passwordEncoder.encode(password), UserRole.OPERATOR));
        return UserSummary.from(user);
    }

    /** BCrypt accepts at most 72 UTF-8 bytes, so enforce both a useful minimum and its byte-level input limit. */
    public static void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new WeakPasswordException();
        }
    }

    public record LoginResult(String accessToken, Instant expiresAt, String username, UserRole role) {}
    public record UserSummary(Long id, String username, UserRole role, Instant createdAt) {
        static UserSummary from(AppUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
        }
    }

    public static class DuplicateUsernameException extends RuntimeException {}
    public static class WeakPasswordException extends RuntimeException {}
}