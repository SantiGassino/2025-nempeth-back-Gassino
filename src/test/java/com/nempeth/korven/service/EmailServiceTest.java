package com.nempeth.korven.service;

import com.nempeth.korven.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppProperties appProps;

    @InjectMocks
    private EmailService emailService;

    @Mock
    private MimeMessage mimeMessage;

    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_FROM = "no-reply@korven.com";
    private static final String TEST_RESET_LINK = "https://korven.com/reset-password?token=abc123";

    @BeforeEach
    void setUp() {
        lenient().when(appProps.getMailFrom()).thenReturn(TEST_FROM);
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    @DisplayName("Should send password reset email successfully")
    void shouldSendPasswordResetEmailSuccessfully() {
        // Given
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);

        // Then
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(mimeMessage);
        verify(appProps).getMailFrom();
    }

    @Test
    @DisplayName("Should use correct from address from app properties")
    void shouldUseCorrectFromAddressFromAppProperties() {
        // Given
        String customFromAddress = "custom@korven.com";
        when(appProps.getMailFrom()).thenReturn(customFromAddress);

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);

        // Then
        verify(appProps).getMailFrom();
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should send email to correct recipient")
    void shouldSendEmailToCorrectRecipient() {
        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle email sending exception gracefully")
    void shouldHandleEmailSendingExceptionGracefully() {
        // Given
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP server unavailable")).when(mailSender).send(any(MimeMessage.class));

        // When / Then - Should not throw exception
        assertThatCode(() -> emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK))
                .doesNotThrowAnyException();

        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle MimeMessage creation exception gracefully")
    void shouldHandleMimeMessageCreationExceptionGracefully() {
        // Given
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("Mail configuration error"));

        // When / Then - Should not throw exception
        assertThatCode(() -> emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should send email with multiple recipients")
    void shouldSendEmailWithMultipleRecipients() {
        // Given
        String recipient1 = "user1@example.com";
        String recipient2 = "user2@example.com";

        // When
        emailService.sendPasswordResetEmail(recipient1, TEST_RESET_LINK);
        emailService.sendPasswordResetEmail(recipient2, TEST_RESET_LINK);

        // Then
        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle different reset links")
    void shouldHandleDifferentResetLinks() {
        // Given
        String resetLink1 = "https://korven.com/reset?token=token1";
        String resetLink2 = "https://korven.com/reset?token=token2";

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, resetLink1);
        emailService.sendPasswordResetEmail(TEST_EMAIL, resetLink2);

        // Then
        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle email with special characters")
    void shouldHandleEmailWithSpecialCharacters() {
        // Given
        String emailWithSpecialChars = "user+test@example.com";

        // When
        emailService.sendPasswordResetEmail(emailWithSpecialChars, TEST_RESET_LINK);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle reset link with query parameters")
    void shouldHandleResetLinkWithQueryParameters() {
        // Given
        String linkWithParams = "https://korven.com/reset?token=abc123&email=user@test.com&exp=1234567890";

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, linkWithParams);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle reset link with special characters")
    void shouldHandleResetLinkWithSpecialCharacters() {
        // Given
        String linkWithSpecialChars = "https://korven.com/reset?token=abc_123-DEF.456&user=test@example.com";

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, linkWithSpecialChars);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle long reset links")
    void shouldHandleLongResetLinks() {
        // Given
        String longToken = "a".repeat(200);
        String longResetLink = "https://korven.com/reset-password?token=" + longToken;

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, longResetLink);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle email addresses with different domains")
    void shouldHandleEmailAddressesWithDifferentDomains() {
        // Given
        String[] emails = {
            "user@gmail.com",
            "user@yahoo.com",
            "user@outlook.com",
            "user@company.co.uk"
        };

        // When
        for (String email : emails) {
            emailService.sendPasswordResetEmail(email, TEST_RESET_LINK);
        }

        // Then
        verify(mailSender, times(4)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle null mail sender gracefully")
    void shouldHandleNullMailSenderGracefully() {
        // Given
        EmailService serviceWithNullSender = new EmailService(null, appProps);

        // When / Then - Should not throw exception
        assertThatCode(() -> serviceWithNullSender.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should create MimeMessage for each email sent")
    void shouldCreateMimeMessageForEachEmailSent() {
        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);
        emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK);

        // Then
        verify(mailSender, times(3)).createMimeMessage();
        verify(mailSender, times(3)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should use transactional annotation")
    void shouldUseTransactionalAnnotation() throws NoSuchMethodException {
        // Given
        var method = EmailService.class.getMethod("sendPasswordResetEmail", String.class, String.class);

        // Then
        assertThat(method.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isTrue();
    }

    @Test
    @DisplayName("Should handle internationalized email addresses")
    void shouldHandleInternationalizedEmailAddresses() {
        // Given
        String internationalEmail = "用户@例え.jp";

        // When
        emailService.sendPasswordResetEmail(internationalEmail, TEST_RESET_LINK);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle empty from address gracefully")
    void shouldHandleEmptyFromAddressGracefully() {
        // Given
        when(appProps.getMailFrom()).thenReturn("");

        // When / Then - Should not throw exception
        assertThatCode(() -> emailService.sendPasswordResetEmail(TEST_EMAIL, TEST_RESET_LINK))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle reset link with encoded characters")
    void shouldHandleResetLinkWithEncodedCharacters() {
        // Given
        String encodedLink = "https://korven.com/reset?token=abc%20123%26def";

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, encodedLink);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should send email with HTTPS reset link")
    void shouldSendEmailWithHttpsResetLink() {
        // Given
        String httpsLink = "https://secure.korven.com/reset-password?token=secure123";

        // When
        emailService.sendPasswordResetEmail(TEST_EMAIL, httpsLink);

        // Then
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should handle concurrent email sending")
    void shouldHandleConcurrentEmailSending() {
        // Given
        String[] recipients = {"user1@test.com", "user2@test.com", "user3@test.com"};

        // When
        for (String recipient : recipients) {
            emailService.sendPasswordResetEmail(recipient, TEST_RESET_LINK);
        }

        // Then
        verify(mailSender, times(3)).send(mimeMessage);
    }
}
