package com.nempeth.korven.service;

import com.nempeth.korven.config.AppProperties;
import com.nempeth.korven.persistence.entity.PasswordResetToken;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.PasswordResetTokenRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private AppProperties appProps;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User testUser;
    private PasswordResetToken testToken;
    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD = "newPassword123";
    private static final String TEST_FRONTEND_URL = "https://korven.com";
    private static final int TEST_TOKEN_TTL = 30;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .name("John")
                .lastName("Doe")
                .passwordHash("$2a$10$oldPasswordHash")
                .build();

        testToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token("validToken123")
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .createdAt(OffsetDateTime.now())
                .usedAt(null)
                .build();

        lenient().when(appProps.getFrontendBaseUrl()).thenReturn(TEST_FRONTEND_URL);
        lenient().when(appProps.getResetTokenTtlMinutes()).thenReturn(TEST_TOKEN_TTL);
    }

    // ==================== START RESET TESTS ====================

    @Test
    @DisplayName("Should start password reset for existing user")
    void shouldStartPasswordResetForExistingUser() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        verify(userRepository).findByEmailIgnoreCase(TEST_EMAIL);
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), anyString());
    }

    @Test
    @DisplayName("Should generate secure random token")
    void shouldGenerateSecureRandomToken() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        
        assertThat(savedToken.getToken()).isNotNull();
        assertThat(savedToken.getToken()).isNotEmpty();
        assertThat(savedToken.getToken().length()).isGreaterThan(40); // Base64 encoded 32 bytes
    }

    @Test
    @DisplayName("Should set token expiration based on configured TTL")
    void shouldSetTokenExpirationBasedOnConfiguredTtl() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime beforeTest = OffsetDateTime.now();

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        
        OffsetDateTime expectedExpiry = beforeTest.plusMinutes(TEST_TOKEN_TTL);
        assertThat(savedToken.getExpiresAt()).isAfterOrEqualTo(expectedExpiry.minusSeconds(2));
        assertThat(savedToken.getExpiresAt()).isBeforeOrEqualTo(expectedExpiry.plusSeconds(2));
    }

    @Test
    @DisplayName("Should send email with correct reset link")
    void shouldSendEmailWithCorrectResetLink() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), linkCaptor.capture());
        String resetLink = linkCaptor.getValue();
        
        assertThat(resetLink).startsWith(TEST_FRONTEND_URL);
        assertThat(resetLink).contains("/reset-password?token=");
    }

    @Test
    @DisplayName("Should not reveal if email does not exist")
    void shouldNotRevealIfEmailDoesNotExist() {
        // Given
        when(userRepository.findByEmailIgnoreCase("nonexistent@example.com")).thenReturn(Optional.empty());

        // When
        passwordResetService.startReset("nonexistent@example.com", request);

        // Then
        verify(userRepository).findByEmailIgnoreCase("nonexistent@example.com");
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    @DisplayName("Should handle email lookup case-insensitively")
    void shouldHandleEmailLookupCaseInsensitively() {
        // Given
        String upperCaseEmail = "USER@EXAMPLE.COM";
        when(userRepository.findByEmailIgnoreCase(upperCaseEmail)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(upperCaseEmail, request);

        // Then
        verify(userRepository).findByEmailIgnoreCase(upperCaseEmail);
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Should remove trailing slashes from frontend URL")
    void shouldRemoveTrailingSlashesFromFrontendUrl() {
        // Given
        when(appProps.getFrontendBaseUrl()).thenReturn("https://korven.com///");
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(TEST_EMAIL), linkCaptor.capture());
        String resetLink = linkCaptor.getValue();
        
        assertThat(resetLink).isEqualTo("https://korven.com/reset-password?token=" + resetLink.split("token=")[1]);
        assertThat(resetLink).doesNotContain("///reset-password");
    }

    @Test
    @DisplayName("Should create token with user association")
    void shouldCreateTokenWithUserAssociation() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        PasswordResetToken savedToken = tokenCaptor.getValue();
        
        assertThat(savedToken.getUser()).isEqualTo(testUser);
        assertThat(savedToken.getId()).isNotNull();
        assertThat(savedToken.getCreatedAt()).isNotNull();
        assertThat(savedToken.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("Should generate different tokens for multiple requests")
    void shouldGenerateDifferentTokensForMultipleRequests() {
        // Given
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        passwordResetService.startReset(TEST_EMAIL, request);
        passwordResetService.startReset(TEST_EMAIL, request);

        // Then
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository, times(2)).save(tokenCaptor.capture());
        
        String token1 = tokenCaptor.getAllValues().get(0).getToken();
        String token2 = tokenCaptor.getAllValues().get(1).getToken();
        
        assertThat(token1).isNotEqualTo(token2);
    }

    // ==================== VALIDATE TOKEN TESTS ====================

    @Test
    @DisplayName("Should validate token successfully when valid and not used")
    void shouldValidateTokenSuccessfullyWhenValidAndNotUsed() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        // When
        boolean isValid = passwordResetService.validateToken("validToken");

        // Then
        assertThat(isValid).isTrue();
        verify(tokenRepository).findByToken("validToken");
    }

    @Test
    @DisplayName("Should reject expired token")
    void shouldRejectExpiredToken() {
        // Given
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .token("expiredToken")
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("expiredToken")).thenReturn(Optional.of(expiredToken));

        // When
        boolean isValid = passwordResetService.validateToken("expiredToken");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject already used token")
    void shouldRejectAlreadyUsedToken() {
        // Given
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .token("usedToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(OffsetDateTime.now().minusMinutes(5))
                .build();
        when(tokenRepository.findByToken("usedToken")).thenReturn(Optional.of(usedToken));

        // When
        boolean isValid = passwordResetService.validateToken("usedToken");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should reject non-existent token")
    void shouldRejectNonExistentToken() {
        // Given
        when(tokenRepository.findByToken("nonExistentToken")).thenReturn(Optional.empty());

        // When
        boolean isValid = passwordResetService.validateToken("nonExistentToken");

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle null token gracefully")
    void shouldHandleNullTokenGracefully() {
        // Given
        when(tokenRepository.findByToken(null)).thenReturn(Optional.empty());

        // When
        boolean isValid = passwordResetService.validateToken(null);

        // Then
        assertThat(isValid).isFalse();
    }

    @Test
    @DisplayName("Should handle empty token string")
    void shouldHandleEmptyTokenString() {
        // Given
        when(tokenRepository.findByToken("")).thenReturn(Optional.empty());

        // When
        boolean isValid = passwordResetService.validateToken("");

        // Then
        assertThat(isValid).isFalse();
    }

    // ==================== RESET PASSWORD TESTS ====================

    @Test
    @DisplayName("Should reset password with valid token")
    void shouldResetPasswordWithValidToken() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        String oldPasswordHash = testUser.getPasswordHash();

        // When
        passwordResetService.resetPassword("validToken", TEST_PASSWORD);

        // Then
        assertThat(testUser.getPasswordHash()).isNotEqualTo(oldPasswordHash);
        assertThat(testUser.getPasswordHash()).startsWith("$2a$"); // BCrypt hash
        assertThat(validToken.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception for invalid token")
    void shouldThrowExceptionForInvalidToken() {
        // Given
        when(tokenRepository.findByToken("invalidToken")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> passwordResetService.resetPassword("invalidToken", TEST_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token inválido");
    }

    @Test
    @DisplayName("Should throw exception for expired token during reset")
    void shouldThrowExceptionForExpiredTokenDuringReset() {
        // Given
        PasswordResetToken expiredToken = PasswordResetToken.builder()
                .user(testUser)
                .token("expiredToken")
                .expiresAt(OffsetDateTime.now().minusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("expiredToken")).thenReturn(Optional.of(expiredToken));

        // When / Then
        assertThatThrownBy(() -> passwordResetService.resetPassword("expiredToken", TEST_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expirado o ya utilizado");
    }

    @Test
    @DisplayName("Should throw exception for already used token during reset")
    void shouldThrowExceptionForAlreadyUsedTokenDuringReset() {
        // Given
        PasswordResetToken usedToken = PasswordResetToken.builder()
                .user(testUser)
                .token("usedToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(OffsetDateTime.now().minusMinutes(5))
                .build();
        when(tokenRepository.findByToken("usedToken")).thenReturn(Optional.of(usedToken));

        // When / Then
        assertThatThrownBy(() -> passwordResetService.resetPassword("usedToken", TEST_PASSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expirado o ya utilizado");
    }

    @Test
    @DisplayName("Should hash new password using BCrypt")
    void shouldHashNewPasswordUsingBCrypt() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        // When
        passwordResetService.resetPassword("validToken", TEST_PASSWORD);

        // Then
        assertThat(testUser.getPasswordHash()).startsWith("$2a$10$");
        assertThat(testUser.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);
    }

    @Test
    @DisplayName("Should mark token as used after password reset")
    void shouldMarkTokenAsUsedAfterPasswordReset() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        OffsetDateTime beforeReset = OffsetDateTime.now();

        // When
        passwordResetService.resetPassword("validToken", TEST_PASSWORD);

        // Then
        assertThat(validToken.getUsedAt()).isNotNull();
        assertThat(validToken.getUsedAt()).isAfterOrEqualTo(beforeReset.minusSeconds(1));
        assertThat(validToken.getUsedAt()).isBeforeOrEqualTo(OffsetDateTime.now().plusSeconds(1));
    }

    @Test
    @DisplayName("Should update user password in database")
    void shouldUpdateUserPasswordInDatabase() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        // When
        passwordResetService.resetPassword("validToken", "newSecurePassword123");

        // Then
        assertThat(testUser.getPasswordHash()).isNotNull();
        assertThat(testUser.getPasswordHash()).isNotEqualTo("$2a$10$oldPasswordHash");
    }

    @Test
    @DisplayName("Should handle different password lengths")
    void shouldHandleDifferentPasswordLengths() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        // When - short password
        passwordResetService.resetPassword("validToken", "abc123");

        // Then
        assertThat(testUser.getPasswordHash()).isNotNull();
        assertThat(testUser.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    @DisplayName("Should handle special characters in new password")
    void shouldHandleSpecialCharactersInNewPassword() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        String passwordWithSpecialChars = "P@ssw0rd!#$%^&*()";

        // When
        passwordResetService.resetPassword("validToken", passwordWithSpecialChars);

        // Then
        assertThat(testUser.getPasswordHash()).isNotNull();
        assertThat(testUser.getPasswordHash()).startsWith("$2a$");
    }

    @Test
    @DisplayName("Should prevent token reuse after successful reset")
    void shouldPreventTokenReuseAfterSuccessfulReset() {
        // Given
        PasswordResetToken validToken = PasswordResetToken.builder()
                .user(testUser)
                .token("validToken")
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .usedAt(null)
                .build();
        when(tokenRepository.findByToken("validToken")).thenReturn(Optional.of(validToken));

        // When - first use
        passwordResetService.resetPassword("validToken", "firstPassword");

        // Then - token should be marked as used
        assertThat(validToken.getUsedAt()).isNotNull();

        // When / Then - second use should fail
        assertThatThrownBy(() -> passwordResetService.resetPassword("validToken", "secondPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token expirado o ya utilizado");
    }
}
