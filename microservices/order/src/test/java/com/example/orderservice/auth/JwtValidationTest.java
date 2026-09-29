package com.example.orderservice.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtValidationTest {

    private static final Instant NOW = Instant.now();
    private static final SecretKey KEY = key("test-signing-key-material-with-32-bytes-min");

    @Test
    void acceptsSignedUnexpiredTokenWithExpectedIssuer() {
        Jwt decoded = decoder(KEY).decode(token(KEY, NOW, NOW.plusSeconds(60), AuthService.ISSUER));
        assertEquals("operator", decoded.getSubject());
    }

    @Test
    void rejectsInvalidSignature() {
        String token = token(KEY, NOW, NOW.plusSeconds(60), AuthService.ISSUER);
        assertThrows(JwtException.class, () -> decoder(key("different-signing-key-material-with-32-bytes")).decode(token));
    }

    @Test
    void rejectsExpiredToken() {
        String token = token(KEY, NOW.minusSeconds(120), NOW.minusSeconds(60), AuthService.ISSUER);
        assertThrows(JwtException.class, () -> decoder(KEY).decode(token));
    }

    @Test
    void rejectsUnexpectedIssuer() {
        String token = token(KEY, NOW, NOW.plusSeconds(60), "untrusted-issuer");
        assertThrows(JwtException.class, () -> decoder(KEY).decode(token));
    }

    private static JwtDecoder decoder(SecretKey key) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(AuthService.ISSUER));
        return decoder;
    }

    private static String token(SecretKey key, Instant issuedAt, Instant expiresAt, String issuer) {
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("operator")
                .issuedAt(issuedAt).expiresAt(expiresAt).claim("roles", "OPERATOR").build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private static SecretKey key(String value) {
        return new SecretKeySpec(value.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}