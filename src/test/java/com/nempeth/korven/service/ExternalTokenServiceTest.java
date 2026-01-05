package com.nempeth.korven.service;

import com.nempeth.korven.rest.dto.ExternalTokenRequest;
import com.nempeth.korven.rest.dto.ExternalTokenResponse;
import com.nempeth.korven.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalTokenService Tests")
class ExternalTokenServiceTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ExternalTokenCacheService tokenCacheService;

    @InjectMocks
    private ExternalTokenService externalTokenService;

    @Captor
    private ArgumentCaptor<String> clientNameCaptor;

    @Captor
    private ArgumentCaptor<String> tokenCaptor;

    @Captor
    private ArgumentCaptor<Map<String, Object>> claimsCaptor;

    private ExternalTokenRequest request;
    private String testClientName;

    @BeforeEach
    void setUp() {
        testClientName = "TestClient";
        request = ExternalTokenRequest.builder()
                .clientName(testClientName)
                .build();
    }

    @Test
    @DisplayName("Should generate token successfully for new client")
    void shouldGenerateTokenSuccessfullyForNewClient() {
        // Given
        String generatedToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test";
        when(tokenCacheService.getActiveToken(testClientName)).thenReturn(null);
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo(generatedToken);
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.getMessage()).contains(testClientName);
        assertThat(response.getMessage()).contains("24 horas");

        verify(tokenCacheService).getActiveToken(testClientName);
        verify(jwtUtils).generateExternalApiToken(eq("external-api"), anyMap());
        verify(tokenCacheService).storeToken(testClientName, generatedToken);
    }

    @Test
    @DisplayName("Should invalidate old token when generating new token")
    void shouldInvalidateOldTokenWhenGeneratingNewToken() {
        // Given
        String oldToken = "oldToken123";
        String newToken = "newToken456";
        when(tokenCacheService.getActiveToken(testClientName)).thenReturn(oldToken);
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(newToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(request);

        // Then
        assertThat(response.getToken()).isEqualTo(newToken);
        verify(tokenCacheService).getActiveToken(testClientName);
        verify(tokenCacheService).storeToken(testClientName, newToken);
    }

    @Test
    @DisplayName("Should create token with correct claims")
    void shouldCreateTokenWithCorrectClaims() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), claimsCaptor.capture()))
                .thenReturn(generatedToken);

        // When
        externalTokenService.generateToken(request);

        // Then
        Map<String, Object> capturedClaims = claimsCaptor.getValue();
        assertThat(capturedClaims).isNotNull();
        assertThat(capturedClaims).containsKey("type");
        assertThat(capturedClaims.get("type")).isEqualTo("API_KEY");
        assertThat(capturedClaims).containsKey("clientName");
        assertThat(capturedClaims.get("clientName")).isEqualTo(testClientName);
        assertThat(capturedClaims).containsKey("timestamp");
        assertThat(capturedClaims.get("timestamp")).isInstanceOf(Long.class);
    }

    @Test
    @DisplayName("Should format expiration date in ISO 8601 format")
    void shouldFormatExpirationDateInISO8601Format() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(request);

        // Then
        assertThat(response.getExpiresAt()).isNotNull();
        // Verify it's a valid ISO 8601 format
        assertThat(response.getExpiresAt()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        
        // Verify it can be parsed
        Instant expirationInstant = Instant.parse(response.getExpiresAt());
        assertThat(expirationInstant).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should set expiration to approximately 24 hours from now")
    void shouldSetExpirationToApproximately24HoursFromNow() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        long beforeGeneration = System.currentTimeMillis();

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(request);

        long afterGeneration = System.currentTimeMillis();

        // Then
        Instant expirationInstant = Instant.parse(response.getExpiresAt());
        long expirationMs = expirationInstant.toEpochMilli();
        
        // Should be approximately 24 hours from now
        long expectedExpirationMin = beforeGeneration + (24 * 60 * 60 * 1000);
        long expectedExpirationMax = afterGeneration + (24 * 60 * 60 * 1000);
        
        assertThat(expirationMs).isBetween(expectedExpirationMin, expectedExpirationMax);
    }

    @Test
    @DisplayName("Should store generated token in cache")
    void shouldStoreGeneratedTokenInCache() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        externalTokenService.generateToken(request);

        // Then
        verify(tokenCacheService).storeToken(clientNameCaptor.capture(), tokenCaptor.capture());
        assertThat(clientNameCaptor.getValue()).isEqualTo(testClientName);
        assertThat(tokenCaptor.getValue()).isEqualTo(generatedToken);
    }

    @Test
    @DisplayName("Should generate different tokens for different clients")
    void shouldGenerateDifferentTokensForDifferentClients() {
        // Given
        ExternalTokenRequest request1 = ExternalTokenRequest.builder().clientName("Client1").build();
        ExternalTokenRequest request2 = ExternalTokenRequest.builder().clientName("Client2").build();
        
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap()))
                .thenReturn("token1", "token2");

        // When
        ExternalTokenResponse response1 = externalTokenService.generateToken(request1);
        ExternalTokenResponse response2 = externalTokenService.generateToken(request2);

        // Then
        assertThat(response1.getToken()).isEqualTo("token1");
        assertThat(response2.getToken()).isEqualTo("token2");
        verify(tokenCacheService).storeToken("Client1", "token1");
        verify(tokenCacheService).storeToken("Client2", "token2");
    }

    @Test
    @DisplayName("Should include client name in success message")
    void shouldIncludeClientNameInSuccessMessage() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(request);

        // Then
        assertThat(response.getMessage()).contains(testClientName);
        assertThat(response.getMessage()).contains("Token generado exitosamente");
    }

    @Test
    @DisplayName("Should handle client names with special characters")
    void shouldHandleClientNamesWithSpecialCharacters() {
        // Given
        String specialClientName = "Client-Name_123@Test";
        ExternalTokenRequest specialRequest = ExternalTokenRequest.builder()
                .clientName(specialClientName)
                .build();
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(specialRequest);

        // Then
        verify(tokenCacheService).getActiveToken(specialClientName);
        verify(tokenCacheService).storeToken(specialClientName, generatedToken);
        assertThat(response.getMessage()).contains(specialClientName);
    }

    @Test
    @DisplayName("Should use external-api as subject for all tokens")
    void shouldUseExternalApiAsSubjectForAllTokens() {
        // Given
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        externalTokenService.generateToken(request);

        // Then
        verify(jwtUtils).generateExternalApiToken(eq("external-api"), anyMap());
    }

    @Test
    @DisplayName("Should include timestamp in token claims")
    void shouldIncludeTimestampInTokenClaims() {
        // Given
        String generatedToken = "token123";
        long beforeGeneration = System.currentTimeMillis();
        
        when(jwtUtils.generateExternalApiToken(eq("external-api"), claimsCaptor.capture()))
                .thenReturn(generatedToken);

        // When
        externalTokenService.generateToken(request);
        
        long afterGeneration = System.currentTimeMillis();

        // Then
        Map<String, Object> claims = claimsCaptor.getValue();
        Long timestamp = (Long) claims.get("timestamp");
        assertThat(timestamp).isNotNull();
        assertThat(timestamp).isBetween(beforeGeneration, afterGeneration);
    }

    @Test
    @DisplayName("Should handle very long client names")
    void shouldHandleVeryLongClientNames() {
        // Given
        String longClientName = "A".repeat(500);
        ExternalTokenRequest longRequest = ExternalTokenRequest.builder()
                .clientName(longClientName)
                .build();
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(longRequest);

        // Then
        verify(tokenCacheService).storeToken(longClientName, generatedToken);
        assertThat(response.getToken()).isEqualTo(generatedToken);
    }

    @Test
    @DisplayName("Should regenerate token when called multiple times for same client")
    void shouldRegenerateTokenWhenCalledMultipleTimesForSameClient() {
        // Given
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap()))
                .thenReturn("token1", "token2", "token3");
        when(tokenCacheService.getActiveToken(testClientName))
                .thenReturn(null, "token1", "token2");

        // When
        ExternalTokenResponse response1 = externalTokenService.generateToken(request);
        ExternalTokenResponse response2 = externalTokenService.generateToken(request);
        ExternalTokenResponse response3 = externalTokenService.generateToken(request);

        // Then
        assertThat(response1.getToken()).isEqualTo("token1");
        assertThat(response2.getToken()).isEqualTo("token2");
        assertThat(response3.getToken()).isEqualTo("token3");
        verify(jwtUtils, times(3)).generateExternalApiToken(eq("external-api"), anyMap());
    }

    @Test
    @DisplayName("Should handle whitespace in client names")
    void shouldHandleWhitespaceInClientNames() {
        // Given
        String clientNameWithSpaces = "Client With Spaces";
        ExternalTokenRequest spaceRequest = ExternalTokenRequest.builder()
                .clientName(clientNameWithSpaces)
                .build();
        String generatedToken = "token123";
        when(jwtUtils.generateExternalApiToken(eq("external-api"), anyMap())).thenReturn(generatedToken);

        // When
        ExternalTokenResponse response = externalTokenService.generateToken(spaceRequest);

        // Then
        verify(tokenCacheService).storeToken(clientNameWithSpaces, generatedToken);
        assertThat(response.getMessage()).contains(clientNameWithSpaces);
    }
}
