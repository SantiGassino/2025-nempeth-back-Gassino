package com.nempeth.korven.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("JwtUtils Tests")
class JwtUtilsTest {

    private JwtUtils jwtUtils;
    private static final String TEST_SECRET = "ThisIsAVerySecretKeyForTestingPurposesItMustBeAtLeast256BitsLong";
    private static final long TEST_EXPIRATION_MS = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils(TEST_SECRET, TEST_EXPIRATION_MS);
    }

    @Test
    @DisplayName("Should generate token with subject and claims")
    void shouldGenerateTokenWithSubjectAndClaims() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");
        claims.put("userId", 123);

        // When
        String token = jwtUtils.generateToken(subject, claims);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    @DisplayName("Should parse generated token correctly")
    void shouldParseGeneratedTokenCorrectly() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "admin");
        claims.put("userId", 123);
        String token = jwtUtils.generateToken(subject, claims);

        // When
        Jws<Claims> parsed = jwtUtils.parseToken(token);

        // Then
        assertThat(parsed).isNotNull();
        assertThat(parsed.getBody().getSubject()).isEqualTo(subject);
        assertThat(parsed.getBody().get("role")).isEqualTo("admin");
        assertThat(parsed.getBody().get("userId")).isEqualTo(123);
    }

    @Test
    @DisplayName("Should set expiration date for regular token")
    void shouldSetExpirationDateForRegularToken() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        long beforeGeneration = System.currentTimeMillis();

        // When
        String token = jwtUtils.generateToken(subject, claims);
        long afterGeneration = System.currentTimeMillis();

        // Then
        Date expiration = jwtUtils.getExpirationFromToken(token);
        assertThat(expiration).isNotNull();
        
        long expectedExpirationMin = beforeGeneration + TEST_EXPIRATION_MS - 2000; // Allow 2 sec tolerance
        long expectedExpirationMax = afterGeneration + TEST_EXPIRATION_MS + 2000;
        
        assertThat(expiration.getTime()).isBetween(expectedExpirationMin, expectedExpirationMax);
    }

    @Test
    @DisplayName("Should set issued at date")
    void shouldSetIssuedAtDate() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        long beforeGeneration = System.currentTimeMillis();

        // When
        String token = jwtUtils.generateToken(subject, claims);
        long afterGeneration = System.currentTimeMillis();

        // Then
        Jws<Claims> parsed = jwtUtils.parseToken(token);
        Date issuedAt = parsed.getBody().getIssuedAt();
        
        assertThat(issuedAt).isNotNull();
        assertThat(issuedAt.getTime()).isBetween(beforeGeneration - 2000, afterGeneration + 2000); // Allow 2 sec tolerance
    }

    @Test
    @DisplayName("Should generate permanent API key without expiration")
    void shouldGeneratePermanentApiKeyWithoutExpiration() {
        // Given
        String identifier = "permanent-api-key";
        Map<String, Object> claims = new HashMap<>();
        claims.put("apiVersion", "v1");

        // When
        String token = jwtUtils.generatePermanentApiKey(identifier, claims);

        // Then
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        
        Jws<Claims> parsed = jwtUtils.parseToken(token);
        assertThat(parsed.getBody().getSubject()).isEqualTo(identifier);
        assertThat(parsed.getBody().getExpiration()).isNull(); // No expiration
        assertThat(parsed.getBody().get("apiVersion")).isEqualTo("v1");
    }

    @Test
    @DisplayName("Should generate external API token with 24 hours expiration")
    void shouldGenerateExternalApiTokenWith24HoursExpiration() {
        // Given
        String identifier = "external-api";
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "API_KEY");
        long beforeGeneration = System.currentTimeMillis();

        // When
        String token = jwtUtils.generateExternalApiToken(identifier, claims);
        long afterGeneration = System.currentTimeMillis();

        // Then
        assertThat(token).isNotNull();
        
        Date expiration = jwtUtils.getExpirationFromToken(token);
        assertThat(expiration).isNotNull();
        
        long twentyFourHoursMs = 24 * 60 * 60 * 1000;
        long expectedExpirationMin = beforeGeneration + twentyFourHoursMs - 2000; // Allow 2 sec tolerance
        long expectedExpirationMax = afterGeneration + twentyFourHoursMs + 2000;
        
        assertThat(expiration.getTime()).isBetween(expectedExpirationMin, expectedExpirationMax);
    }

    @Test
    @DisplayName("Should parse external API token correctly")
    void shouldParseExternalApiTokenCorrectly() {
        // Given
        String identifier = "external-api";
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "API_KEY");
        claims.put("clientName", "TestClient");
        claims.put("timestamp", System.currentTimeMillis());

        // When
        String token = jwtUtils.generateExternalApiToken(identifier, claims);
        Jws<Claims> parsed = jwtUtils.parseToken(token);

        // Then
        assertThat(parsed.getBody().getSubject()).isEqualTo(identifier);
        assertThat(parsed.getBody().get("type")).isEqualTo("API_KEY");
        assertThat(parsed.getBody().get("clientName")).isEqualTo("TestClient");
        assertThat(parsed.getBody().get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle empty claims map")
    void shouldHandleEmptyClaimsMap() {
        // Given
        String subject = "testUser";
        Map<String, Object> emptyClaims = new HashMap<>();

        // When
        String token = jwtUtils.generateToken(subject, emptyClaims);

        // Then
        assertThat(token).isNotNull();
        Jws<Claims> parsed = jwtUtils.parseToken(token);
        assertThat(parsed.getBody().getSubject()).isEqualTo(subject);
    }

    @Test
    @DisplayName("Should handle various claim types")
    void shouldHandleVariousClaimTypes() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        claims.put("stringClaim", "value");
        claims.put("intClaim", 123);
        claims.put("longClaim", 123456789L);
        claims.put("booleanClaim", true);
        claims.put("doubleClaim", 123.45);

        // When
        String token = jwtUtils.generateToken(subject, claims);
        Jws<Claims> parsed = jwtUtils.parseToken(token);

        // Then
        assertThat(parsed.getBody().get("stringClaim")).isEqualTo("value");
        assertThat(parsed.getBody().get("intClaim")).isEqualTo(123);
        assertThat(parsed.getBody().get("longClaim")).isEqualTo(123456789);
        assertThat(parsed.getBody().get("booleanClaim")).isEqualTo(true);
        assertThat(parsed.getBody().get("doubleClaim")).isEqualTo(123.45);
    }

    @Test
    @DisplayName("Should generate different tokens for different subjects")
    void shouldGenerateDifferentTokensForDifferentSubjects() {
        // Given
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "user");

        // When
        String token1 = jwtUtils.generateToken("user1", claims);
        String token2 = jwtUtils.generateToken("user2", claims);

        // Then
        assertThat(token1).isNotEqualTo(token2);
        
        Jws<Claims> parsed1 = jwtUtils.parseToken(token1);
        Jws<Claims> parsed2 = jwtUtils.parseToken(token2);
        
        assertThat(parsed1.getBody().getSubject()).isEqualTo("user1");
        assertThat(parsed2.getBody().getSubject()).isEqualTo("user2");
    }

    @Test
    @DisplayName("Should generate different tokens when called multiple times")
    void shouldGenerateDifferentTokensWhenCalledMultipleTimes() throws InterruptedException {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();

        // When
        String token1 = jwtUtils.generateToken(subject, claims);
        Thread.sleep(1100); // JWT uses second precision, need at least 1 second delay
        String token2 = jwtUtils.generateToken(subject, claims);

        // Then
        assertThat(token1).isNotEqualTo(token2); // Different due to different issuedAt
    }

    @Test
    @DisplayName("Should extract expiration from token")
    void shouldExtractExpirationFromToken() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        String token = jwtUtils.generateToken(subject, claims);

        // When
        Date expiration = jwtUtils.getExpirationFromToken(token);

        // Then
        assertThat(expiration).isNotNull();
        assertThat(expiration).isAfter(new Date());
    }

    @Test
    @DisplayName("Should handle special characters in subject")
    void shouldHandleSpecialCharactersInSubject() {
        // Given
        String subject = "user@example.com";
        Map<String, Object> claims = new HashMap<>();

        // When
        String token = jwtUtils.generateToken(subject, claims);

        // Then
        Jws<Claims> parsed = jwtUtils.parseToken(token);
        assertThat(parsed.getBody().getSubject()).isEqualTo(subject);
    }

    @Test
    @DisplayName("Should handle special characters in claims")
    void shouldHandleSpecialCharactersInClaims() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", "user@example.com");
        claims.put("name", "José García");
        claims.put("description", "Test with special chars: @#$%");

        // When
        String token = jwtUtils.generateToken(subject, claims);
        Jws<Claims> parsed = jwtUtils.parseToken(token);

        // Then
        assertThat(parsed.getBody().get("email")).isEqualTo("user@example.com");
        assertThat(parsed.getBody().get("name")).isEqualTo("José García");
        assertThat(parsed.getBody().get("description")).isEqualTo("Test with special chars: @#$%");
    }

    @Test
    @DisplayName("Should use HS256 signature algorithm")
    void shouldUseHS256SignatureAlgorithm() {
        // Given
        String subject = "testUser";
        Map<String, Object> claims = new HashMap<>();

        // When
        String token = jwtUtils.generateToken(subject, claims);

        // Then
        String[] parts = token.split("\\.");
        assertThat(parts).hasSize(3);
        // The token should be properly formatted for HS256
        assertThat(token).matches("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");
    }

    @Test
    @DisplayName("Should preserve claim values precisely")
    void shouldPreserveClaimValuesPrecisely() {
        // Given
        String subject = "testUser";
        long timestamp = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("timestamp", timestamp);
        claims.put("count", 42);
        claims.put("rate", 99.99);

        // When
        String token = jwtUtils.generateToken(subject, claims);
        Jws<Claims> parsed = jwtUtils.parseToken(token);

        // Then
        assertThat(parsed.getBody().get("timestamp", Long.class)).isEqualTo(timestamp);
        assertThat(parsed.getBody().get("count", Integer.class)).isEqualTo(42);
        assertThat(parsed.getBody().get("rate", Double.class)).isCloseTo(99.99, within(0.01));
    }
}
