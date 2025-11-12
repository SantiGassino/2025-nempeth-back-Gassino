package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.persistence.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Test")
                .lastName("User")
                .passwordHash("hashedPassword123")
                .build();
    }

    @Test
    @DisplayName("Should find user by email ignoring case")
    void shouldFindUserByEmailIgnoreCase() {
        entityManager.persist(testUser);
        entityManager.flush();

        Optional<User> foundUpperCase = userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM");
        Optional<User> foundLowerCase = userRepository.findByEmailIgnoreCase("test@example.com");
        Optional<User> foundMixedCase = userRepository.findByEmailIgnoreCase("TeSt@ExAmPlE.cOm");

        assertThat(foundUpperCase).isPresent();
        assertThat(foundUpperCase.get().getEmail()).isEqualTo(testUser.getEmail());
        
        assertThat(foundLowerCase).isPresent();
        assertThat(foundLowerCase.get().getEmail()).isEqualTo(testUser.getEmail());
        
        assertThat(foundMixedCase).isPresent();
        assertThat(foundMixedCase.get().getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Should return empty when user not found by email")
    void shouldReturnEmptyWhenUserNotFoundByEmail() {
        Optional<User> found = userRepository.findByEmailIgnoreCase("nonexistent@example.com");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should return true when email exists")
    void shouldReturnTrueWhenEmailExists() {
        entityManager.persist(testUser);
        entityManager.flush();
        boolean exists = userRepository.existsByEmail("test@example.com");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should return false when email does not exist")
    void shouldReturnFalseWhenEmailDoesNotExist() {
        boolean exists = userRepository.existsByEmail("nonexistent@example.com");
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should save user with pre-persist UUID generation")
    void shouldSaveUserWithPrePersistUuidGeneration() {
        User newUser = User.builder()
                .email("newuser@example.com")
                .name("New")
                .lastName("User")
                .passwordHash("hashedPassword456")
                .build();

        User saved = userRepository.save(newUser);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("newuser@example.com");
        assertThat(saved.getName()).isEqualTo("New");
        assertThat(saved.getLastName()).isEqualTo("User");
    }

    @Test
    @DisplayName("Should update existing user")
    void shouldUpdateExistingUser() {
        entityManager.persist(testUser);
        entityManager.flush();

        testUser.setName("Updated");
        testUser.setLastName("Name");
        User updated = userRepository.save(testUser);

        assertThat(updated.getName()).isEqualTo("Updated");
        assertThat(updated.getLastName()).isEqualTo("Name");
        assertThat(updated.getEmail()).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Should delete user")
    void shouldDeleteUser() {
        entityManager.persist(testUser);
        entityManager.flush();

        userRepository.delete(testUser);

        Optional<User> found = userRepository.findById(testUser.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        entityManager.persist(testUser);
        entityManager.flush();
        entityManager.clear();

        User duplicateEmailUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Duplicate")
                .lastName("User")
                .passwordHash("hashedPassword789")
                .build();

        try {
            userRepository.save(duplicateEmailUser);
            entityManager.flush();
            assertThat(false).as("Expected constraint violation exception").isTrue();
        } catch (Exception e) {
            String message = e.getMessage().toLowerCase();
            boolean hasConstraintRelatedWord = message.contains("constraint") || 
                                              message.contains("violación") || 
                                              message.contains("violation") || 
                                              message.contains("unique") ||
                                              message.contains("unicidad");
            assertThat(hasConstraintRelatedWord).as("Message should contain constraint related word").isTrue();
        }
    }

    @Test
    @DisplayName("Should find user by ID")
    void shouldFindUserById() {
        entityManager.persist(testUser);
        entityManager.flush();
        Optional<User> found = userRepository.findById(testUser.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(testUser.getEmail());
    }
}
