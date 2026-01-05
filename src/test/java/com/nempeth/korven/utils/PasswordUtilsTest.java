package com.nempeth.korven.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordUtilsTest {

    @Test
    @DisplayName("Should hash a password successfully")
    void shouldHashPasswordSuccessfully() {
        // Given
        String rawPassword = "mySecurePassword123";

        // When
        String hash = PasswordUtils.hash(rawPassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(hash).isNotEqualTo(rawPassword); // Hash should be different from raw password
        assertThat(hash).startsWith("$2a$"); // BCrypt hashes start with $2a$
    }

    @Test
    @DisplayName("Should generate different hashes for same password")
    void shouldGenerateDifferentHashesForSamePassword() {
        // Given
        String rawPassword = "password123";

        // When
        String hash1 = PasswordUtils.hash(rawPassword);
        String hash2 = PasswordUtils.hash(rawPassword);

        // Then
        assertThat(hash1).isNotEqualTo(hash2); // BCrypt uses salt, so hashes should differ
    }

    @Test
    @DisplayName("Should match correct password with hash")
    void shouldMatchCorrectPasswordWithHash() {
        // Given
        String rawPassword = "correctPassword";
        String hash = PasswordUtils.hash(rawPassword);

        // When
        boolean matches = PasswordUtils.matches(rawPassword, hash);

        // Then
        assertThat(matches).isTrue();
    }

    @Test
    @DisplayName("Should not match incorrect password with hash")
    void shouldNotMatchIncorrectPasswordWithHash() {
        // Given
        String rawPassword = "correctPassword";
        String wrongPassword = "wrongPassword";
        String hash = PasswordUtils.hash(rawPassword);

        // When
        boolean matches = PasswordUtils.matches(wrongPassword, hash);

        // Then
        assertThat(matches).isFalse();
    }

    @Test
    @DisplayName("Should handle empty password")
    void shouldHandleEmptyPassword() {
        // Given
        String emptyPassword = "";

        // When
        String hash = PasswordUtils.hash(emptyPassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(hash).isNotEmpty();
        assertThat(PasswordUtils.matches(emptyPassword, hash)).isTrue();
    }

    @Test
    @DisplayName("Should handle long passwords within BCrypt limit")
    void shouldHandleLongPasswordsWithinBCryptLimit() {
        // Given
        String longPassword = "a".repeat(70); // Within BCrypt's 72-byte limit

        // When
        String hash = PasswordUtils.hash(longPassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(PasswordUtils.matches(longPassword, hash)).isTrue();
    }

    @Test
    @DisplayName("Should handle passwords with special characters")
    void shouldHandlePasswordsWithSpecialCharacters() {
        // Given
        String specialPassword = "P@ssw0rd!#$%^&*()_+-=[]{}|;:',.<>?/~`";

        // When
        String hash = PasswordUtils.hash(specialPassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(PasswordUtils.matches(specialPassword, hash)).isTrue();
    }

    @Test
    @DisplayName("Should handle passwords with unicode characters")
    void shouldHandlePasswordsWithUnicodeCharacters() {
        // Given
        String unicodePassword = "contraseña_日本語_🔒";

        // When
        String hash = PasswordUtils.hash(unicodePassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(PasswordUtils.matches(unicodePassword, hash)).isTrue();
    }

    @Test
    @DisplayName("Should be case sensitive")
    void shouldBeCaseSensitive() {
        // Given
        String password = "Password123";
        String hash = PasswordUtils.hash(password);

        // When
        boolean matchesLowercase = PasswordUtils.matches("password123", hash);
        boolean matchesUppercase = PasswordUtils.matches("PASSWORD123", hash);
        boolean matchesCorrect = PasswordUtils.matches(password, hash);

        // Then
        assertThat(matchesLowercase).isFalse();
        assertThat(matchesUppercase).isFalse();
        assertThat(matchesCorrect).isTrue();
    }

    @Test
    @DisplayName("Should handle whitespace in passwords")
    void shouldHandleWhitespaceInPasswords() {
        // Given
        String passwordWithSpaces = "my password with spaces";

        // When
        String hash = PasswordUtils.hash(passwordWithSpaces);

        // Then
        assertThat(PasswordUtils.matches(passwordWithSpaces, hash)).isTrue();
        assertThat(PasswordUtils.matches("mypasswordwithspaces", hash)).isFalse();
    }

    @Test
    @DisplayName("Should not match password without leading/trailing spaces")
    void shouldNotMatchPasswordWithoutLeadingTrailingSpaces() {
        // Given
        String passwordWithSpaces = " password ";
        String hash = PasswordUtils.hash(passwordWithSpaces);

        // When
        boolean matchesWithSpaces = PasswordUtils.matches(passwordWithSpaces, hash);
        boolean matchesWithoutSpaces = PasswordUtils.matches("password", hash);

        // Then
        assertThat(matchesWithSpaces).isTrue();
        assertThat(matchesWithoutSpaces).isFalse();
    }

    @Test
    @DisplayName("Should handle numeric-only passwords")
    void shouldHandleNumericOnlyPasswords() {
        // Given
        String numericPassword = "1234567890";

        // When
        String hash = PasswordUtils.hash(numericPassword);

        // Then
        assertThat(hash).isNotNull();
        assertThat(PasswordUtils.matches(numericPassword, hash)).isTrue();
    }

    @Test
    @DisplayName("Should verify hash is of reasonable length")
    void shouldVerifyHashIsOfReasonableLength() {
        // Given
        String password = "testPassword";

        // When
        String hash = PasswordUtils.hash(password);

        // Then
        // BCrypt hashes are typically 60 characters
        assertThat(hash.length()).isEqualTo(60);
    }

    @Test
    @DisplayName("Should handle consecutive identical characters")
    void shouldHandleConsecutiveIdenticalCharacters() {
        // Given
        String password = "aaaaaaaaaa";

        // When
        String hash = PasswordUtils.hash(password);

        // Then
        assertThat(hash).isNotNull();
        assertThat(PasswordUtils.matches(password, hash)).isTrue();
    }

    @Test
    @DisplayName("Should not match similar but different passwords")
    void shouldNotMatchSimilarButDifferentPasswords() {
        // Given
        String password1 = "password1";
        String password2 = "password2";
        String hash = PasswordUtils.hash(password1);

        // When
        boolean matches = PasswordUtils.matches(password2, hash);

        // Then
        assertThat(matches).isFalse();
    }
}
