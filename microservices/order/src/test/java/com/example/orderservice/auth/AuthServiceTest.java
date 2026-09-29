package com.example.orderservice.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private static final String SECRET = "unit-test-hmac-key-material-at-least-32-bytes";

    private AppUserRepository users;
    private AuthenticationManager authenticationManager;
    private BCryptPasswordEncoder passwordEncoder;
    private JwtEncoder jwtEncoder;
    private SecretKey key;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        authenticationManager = mock(AuthenticationManager.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        key = new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        authService = new AuthService(users, authenticationManager, passwordEncoder, jwtEncoder,
            Clock.systemUTC(), AuthService.ISSUER);
    }

    @Test
    void loginVerifiesStoredHashAndIssuesShortLivedSignedToken() {
        AppUser user = new AppUser("operator", passwordEncoder.encode("correct horse battery"), UserRole.OPERATOR);
        ReflectionTestUtils.setField(user, "id", 14L);
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

        AuthService.LoginResult result = authService.login("OPERATOR", "correct horse battery");
        JwtDecoder decoder = decoder(key);
        Jwt decoded = decoder.decode(result.accessToken());

        assertEquals("operator", result.username());
        assertEquals(UserRole.OPERATOR, result.role());
        assertTrue(result.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(14))));
        assertTrue(result.expiresAt().isBefore(Instant.now().plus(Duration.ofMinutes(16))));
        assertEquals(List.of("OPERATOR"), decoded.getClaimAsStringList("roles"));
        assertEquals(14L, ((Number) decoded.getClaim("userId")).longValue());
    }

    @Test
    void invalidPasswordIsRejectedByAuthenticationManager() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad credentials"));
        assertThrows(BadCredentialsException.class, () -> authService.login("operator", "wrong password"));
    }

    @Test
    void operatorProvisioningStoresOnlyBcryptHash() {
        when(users.existsByUsername("new.operator")).thenReturn(false);
        when(users.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        authService.createOperator("New.Operator", "long-enough-password");

        var captor = org.mockito.ArgumentCaptor.forClass(AppUser.class);
        org.mockito.Mockito.verify(users).save(captor.capture());
        String storedPassword = captor.getValue().getPassword();
        assertNotEquals("long-enough-password", storedPassword);
        assertTrue(passwordEncoder.matches("long-enough-password", storedPassword));
        assertEquals(UserRole.OPERATOR, captor.getValue().getRole());
    }

    @Test
    void rejectsPasswordsThatExceedBcryptInputLimit() {
        assertThrows(AuthService.WeakPasswordException.class,
                () -> AuthService.validatePassword("é".repeat(40)));
    }

    private JwtDecoder decoder(SecretKey signingKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AuthService.ISSUER));
        return decoder;
    }
}