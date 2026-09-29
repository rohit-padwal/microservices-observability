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

    /**
     * Verifies credentials and issues a short-lived signed token containing subject, user ID, role, issuer, and expiry.
     *
     * @param username normalized before lookup; matching is case-insensitive
     * @param password plaintext input used only for verification and never persisted
     * @return token and safe account metadata for the login response
     * @throws org.springframework.security.authentication.BadCredentialsException if credentials do not match
     */
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

    /**
     * Creates an operator account; callers must pass the ADMIN-protected controller authorization first.
     * Passwords are encoded before persistence and never included in the returned summary.
     *
     * @param username requested login name; stored in normalized lowercase form
     * @param password initial password meeting the application's length/BCrypt byte limit
     * @return account metadata that excludes the password hash
     * @throws DuplicateUsernameException if another account already owns the normalized name
     * @throws WeakPasswordException if the password is outside accepted bounds
     */
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

    /**
     * Enforces the application's minimum and BCrypt's maximum input size before hashing.
     * @param password candidate plaintext password
     * @throws WeakPasswordException if the password is shorter than 12 characters or exceeds 72 UTF-8 bytes
     */
    public static void validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new WeakPasswordException();
        }
    }

    /** Internal auth result used to build the public login DTO; contains no credential material. */
    public record LoginResult(String accessToken, Instant expiresAt, String username, UserRole role) {}

    /** Safe account metadata for admin provisioning responses; password hash is deliberately excluded. */
    public record UserSummary(Long id, String username, UserRole role, Instant createdAt) {
        static UserSummary from(AppUser user) {
            return new UserSummary(user.getId(), user.getUsername(), user.getRole(), user.getCreatedAt());
        }
    }

    /** Username uniqueness conflict mapped to HTTP 409. */
    public static class DuplicateUsernameException extends RuntimeException {}

    /** Password does not meet the configured character/BCrypt byte limits; mapped to HTTP 400. */
    public static class WeakPasswordException extends RuntimeException {}
}