package com.splitwise.app.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET
            = "abcdefghijklmnopqrstuvwxyz123456";

    private static final long ACCESS_EXPIRATION = 60_000;
    private static final long REFRESH_EXPIRATION = 604_800_000;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                SECRET,
                ACCESS_EXPIRATION,
                REFRESH_EXPIRATION
        );
    }

    @Test
    @DisplayName("Generate access token and extract user id")
    void generateAccessToken_shouldContainUserId() {

        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(
                userId,
                "john@example.com",
                "premium"
        );

        assertNotNull(token);
        assertEquals(userId, jwtService.extractUserId(token));
    }

    @Test
    @DisplayName("Extract subscription tier")
    void extractTier_shouldReturnTier() {

        UUID userId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(
                userId,
                "john@example.com",
                "premium"
        );

        assertEquals("premium", jwtService.extractTier(token));
    }

    @Test
    @DisplayName("Token should be valid")
    void isTokenValid_shouldReturnTrue() {

        String token = jwtService.generateAccessToken(
                UUID.randomUUID(),
                "john@example.com",
                "free"
        );

        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    @DisplayName("Malformed token should be invalid")
    void isTokenValid_shouldReturnFalseForInvalidToken() {

        assertFalse(jwtService.isTokenValid("invalid.jwt.token"));
    }

    @Test
    @DisplayName("Refresh expiration getter")
    void getRefreshExpirationMs_shouldReturnConfiguredValue() {

        assertEquals(
                REFRESH_EXPIRATION,
                jwtService.getRefreshExpirationMs()
        );
    }

    @Test
    @DisplayName("Expired token should be invalid")
    void isTokenValid_shouldReturnFalseForExpiredToken() {

        SecretKey key = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "john@example.com")
                .claim("tier", "premium")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertFalse(jwtService.isTokenValid(expiredToken));
    }

    @Test
    @DisplayName("Missing tier should default to free")
    void extractTier_shouldReturnFreeWhenMissing() {

        SecretKey key = Keys.hmacShaKeyFor(
                SECRET.getBytes(StandardCharsets.UTF_8)
        );

        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "john@example.com")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_EXPIRATION))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertEquals("free", jwtService.extractTier(token));
    }

    @Test
    @DisplayName("Generate raw refresh token")
    void generateRawRefreshToken_shouldReturnUniqueTokens() {

        String token1 = jwtService.generateRawRefreshToken();
        String token2 = jwtService.generateRawRefreshToken();

        assertNotNull(token1);
        assertNotNull(token2);

        assertFalse(token1.isBlank());
        assertFalse(token2.isBlank());

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Constructor should pad short secret")
    void constructor_shouldSupportShortSecret() {

        JwtService service = new JwtService(
                "short-secret",
                ACCESS_EXPIRATION,
                REFRESH_EXPIRATION
        );

        String token = service.generateAccessToken(
                UUID.randomUUID(),
                "john@example.com",
                "free"
        );

        assertTrue(service.isTokenValid(token));
    }
}
