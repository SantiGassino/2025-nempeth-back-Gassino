package com.nempeth.korven.security;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private User testUser;
    private Business testBusiness1;
    private Business testBusiness2;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Test")
                .lastName("User")
                .passwordHash("$2a$10$hashedPassword")
                .build();

        testBusiness1 = Business.builder()
                .id(UUID.randomUUID())
                .name("Business 1")
                .joinCode("ABC12345")
                .build();

        testBusiness2 = Business.builder()
                .id(UUID.randomUUID())
                .name("Business 2")
                .joinCode("XYZ67890")
                .build();
    }

    // ==================== loadUserByUsername Success Tests ====================

    @Test
    void shouldLoadUserWithOwnerRole() {
        BusinessMembership ownerMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness1)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
        assertThat(userDetails.getPassword()).isEqualTo("$2a$10$hashedPassword");
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OWNER");
    }

    @Test
    void shouldLoadUserWithEmployeeRole() {
        BusinessMembership employeeMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness1)
                .user(testUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(employeeMembership));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_EMPLOYEE");
    }

    @Test
    void shouldLoadUserWithMultipleMemberships() {
        BusinessMembership ownerMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness1)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        BusinessMembership employeeMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness2)
                .user(testUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership, employeeMembership));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getAuthorities()).hasSize(2);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_OWNER", "ROLE_EMPLOYEE");
    }

    @Test
    void shouldLoadUserWithNoMemberships() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    @Test
    void shouldLoadUserWithMultipleOwnerRoles() {
        // User is owner of two businesses
        BusinessMembership ownerMembership1 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness1)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        BusinessMembership ownerMembership2 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness2)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership1, ownerMembership2));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails).isNotNull();
        // Should deduplicate to single ROLE_OWNER since we use a Set
        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OWNER");
    }

    // ==================== Case Insensitivity Tests ====================

    @Test
    void shouldLoadUserWithUppercaseEmail() {
        when(userRepository.findByEmailIgnoreCase("TEST@EXAMPLE.COM"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("TEST@EXAMPLE.COM");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("test@example.com");
        verify(userRepository).findByEmailIgnoreCase("TEST@EXAMPLE.COM");
    }

    @Test
    void shouldLoadUserWithMixedCaseEmail() {
        when(userRepository.findByEmailIgnoreCase("TeSt@ExAmPlE.cOm"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("TeSt@ExAmPlE.cOm");

        assertThat(userDetails).isNotNull();
        verify(userRepository).findByEmailIgnoreCase("TeSt@ExAmPlE.cOm");
    }

    // ==================== Exception Tests ====================

    @Test
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("nonexistent@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario no encontrado");

        verify(membershipRepository, never()).findByUserIdAndStatus(any(), any());
    }

    @Test
    void shouldThrowExceptionWithNullEmail() {
        when(userRepository.findByEmailIgnoreCase(null))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    void shouldThrowExceptionWithEmptyEmail() {
        when(userRepository.findByEmailIgnoreCase(""))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(""))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Usuario no encontrado");
    }

    // ==================== Active vs Inactive Memberships Tests ====================

    @Test
    void shouldOnlyIncludeActiveMemberships() {
        BusinessMembership activeMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness1)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(activeMembership));

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails.getAuthorities()).hasSize(1);
        verify(membershipRepository).findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE);
    }

    @Test
    void shouldGetDefaultRoleWhenAllMembershipsInactive() {
        // Repository returns empty list when filtering by ACTIVE status
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails.getAuthorities()).hasSize(1);
        assertThat(userDetails.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_USER");
    }

    // ==================== UserDetails Properties Tests ====================

    @Test
    void shouldReturnCorrectUsername() {
        when(userRepository.findByEmailIgnoreCase("user@domain.com"))
                .thenReturn(Optional.of(User.builder()
                        .id(UUID.randomUUID())
                        .email("user@domain.com")
                        .passwordHash("hash")
                        .build()));
        when(membershipRepository.findByUserIdAndStatus(any(), any()))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("user@domain.com");

        assertThat(userDetails.getUsername()).isEqualTo("user@domain.com");
    }

    @Test
    void shouldReturnCorrectPassword() {
        String expectedHash = "$2a$10$specificHashValue";
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(User.builder()
                        .id(UUID.randomUUID())
                        .email("test@example.com")
                        .passwordHash(expectedHash)
                        .build()));
        when(membershipRepository.findByUserIdAndStatus(any(), any()))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails.getPassword()).isEqualTo(expectedHash);
    }

    @Test
    void shouldReturnUserDetailsWithEnabledAccount() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(testUser.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        // Spring's default User implementation sets these to true
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }

    // ==================== Repository Interaction Tests ====================

    @Test
    void shouldCallRepositoriesWithCorrectParameters() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("test@example.com")
                .passwordHash("hash")
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of());

        userDetailsService.loadUserByUsername("test@example.com");

        verify(userRepository).findByEmailIgnoreCase("test@example.com");
        verify(membershipRepository).findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
    }

    @Test
    void shouldNotCallMembershipRepositoryWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase("nonexistent@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);

        verify(userRepository).findByEmailIgnoreCase("nonexistent@example.com");
        verify(membershipRepository, never()).findByUserIdAndStatus(any(), any());
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleUserWithSpecialCharactersInEmail() {
        String specialEmail = "test+user@example.com";
        User specialUser = User.builder()
                .id(UUID.randomUUID())
                .email(specialEmail)
                .passwordHash("hash")
                .build();

        when(userRepository.findByEmailIgnoreCase(specialEmail))
                .thenReturn(Optional.of(specialUser));
        when(membershipRepository.findByUserIdAndStatus(any(), any()))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername(specialEmail);

        assertThat(userDetails.getUsername()).isEqualTo(specialEmail);
    }

    @Test
    void shouldHandleVeryLongEmail() {
        String longEmail = "very.long.email.address.with.many.dots@subdomain.example.com";
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(longEmail)
                .passwordHash("hash")
                .build();

        when(userRepository.findByEmailIgnoreCase(longEmail))
                .thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndStatus(any(), any()))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername(longEmail);

        assertThat(userDetails.getUsername()).isEqualTo(longEmail);
    }

    @Test
    void shouldHandleEmptyPasswordHash() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("")
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(user));
        when(membershipRepository.findByUserIdAndStatus(any(), any()))
                .thenReturn(List.of());

        UserDetails userDetails = userDetailsService.loadUserByUsername("test@example.com");

        assertThat(userDetails.getPassword()).isEmpty();
    }
}
