package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.ReservationRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.UpdateMembershipRoleRequest;
import com.nempeth.korven.rest.dto.UpdateMembershipStatusRequest;
import com.nempeth.korven.rest.dto.UpdateUserPasswordRequest;
import com.nempeth.korven.rest.dto.UpdateUserProfileRequest;
import com.nempeth.korven.rest.dto.UserResponse;
import com.nempeth.korven.utils.PasswordUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Tests")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private Business testBusiness;
    private BusinessMembership testMembership;
    private UUID userId;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .email("test@example.com")
                .name("Test")
                .lastName("User")
                .passwordHash(PasswordUtils.hash("password123"))
                .build();

        testBusiness = Business.builder()
                .id(businessId)
                .name("Test Business")
                .joinCode("TESTCODE123")
                .build();

        testMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should get user by email successfully")
    void shouldGetUserByEmail() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserIdAndStatus(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(testMembership));

        UserResponse response = userService.getUserByEmail("test@example.com");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("test@example.com");
        assertThat(response.name()).isEqualTo("Test");
        assertThat(response.lastName()).isEqualTo("User");
        assertThat(response.businesses()).hasSize(1);
        
        verify(userRepository).findByEmailIgnoreCase("test@example.com");
        verify(membershipRepository).findByUserIdAndStatus(userId, MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw exception when user not found by email")
    void shouldThrowExceptionWhenUserNotFoundByEmail() {
        when(userRepository.findByEmailIgnoreCase("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserByEmail("nonexistent@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should get user by ID when requester is the same user")
    void shouldGetUserByIdWhenRequesterIsSameUser() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));

        UserResponse response = userService.getUserById(userId, "test@example.com");

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(userId);
        verify(userRepository).findByEmailIgnoreCase("test@example.com");
        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("Should get user by ID when users share a business")
    void shouldGetUserByIdWhenUsersShareBusiness() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder()
                .id(otherUserId)
                .email("other@example.com")
                .name("Other")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership otherMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(otherUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(membershipRepository.findByUserId(otherUserId)).thenReturn(List.of(otherMembership));
        UserResponse response = userService.getUserById(otherUserId, "test@example.com");
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(otherUserId);
    }

    @Test
    @DisplayName("Should throw exception when users don't share a business")
    void shouldThrowExceptionWhenUsersDontShareBusiness() {
        UUID otherUserId = UUID.randomUUID();
        User otherUser = User.builder()
                .id(otherUserId)
                .email("other@example.com")
                .name("Other")
                .lastName("User")
                .passwordHash("hash")
                .build();

        Business otherBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Other Business")
                .joinCode("OTHER123")
                .build();

        BusinessMembership otherMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(otherBusiness)
                .user(otherUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(membershipRepository.findByUserId(otherUserId)).thenReturn(List.of(otherMembership));

        assertThatThrownBy(() -> userService.getUserById(otherUserId, "test@example.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No tienes acceso para ver los datos de este usuario");
    }

    @Test
    @DisplayName("Should update user profile successfully")
    void shouldUpdateUserProfileSuccessfully() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "newemail@example.com",
                "NewName",
                "NewLastName"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmailIgnoreCase("newemail@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        boolean emailChanged = userService.updateUserProfile(userId, "test@example.com", request);

        assertThat(emailChanged).isTrue();
        assertThat(testUser.getEmail()).isEqualTo("newemail@example.com");
        assertThat(testUser.getName()).isEqualTo("NewName");
        assertThat(testUser.getLastName()).isEqualTo("NewLastName");
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should not change email when same email is provided")
    void shouldNotChangeEmailWhenSameEmailProvided() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "test@example.com",
                "NewName",
                "NewLastName"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        boolean emailChanged = userService.updateUserProfile(userId, "test@example.com", request);

        assertThat(emailChanged).isFalse();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when email format is invalid")
    void shouldThrowExceptionWhenEmailFormatInvalid() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "invalid-email",
                "NewName",
                "NewLastName"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserProfile(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email con formato inválido");
    }

    @Test
    @DisplayName("Should throw exception when email already exists")
    void shouldThrowExceptionWhenEmailAlreadyExists() {
        User anotherUser = User.builder()
                .id(UUID.randomUUID())
                .email("existing@example.com")
                .name("Existing")
                .lastName("User")
                .passwordHash("hash")
                .build();

        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "existing@example.com",
                "NewName",
                "NewLastName"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.findByEmailIgnoreCase("existing@example.com")).thenReturn(Optional.of(anotherUser));

        assertThatThrownBy(() -> userService.updateUserProfile(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email ya registrado");
    }

    @Test
    @DisplayName("Should throw exception when unauthorized to update profile")
    void shouldThrowExceptionWhenUnauthorizedToUpdateProfile() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "newemail@example.com",
                "NewName",
                "NewLastName"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        assertThatThrownBy(() -> userService.updateUserProfile(userId, "unauthorized@example.com", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No autorizado para modificar este usuario");
    }

    @Test
    @DisplayName("Should update password successfully")
    void shouldUpdatePasswordSuccessfully() {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                "password123",
                "newPassword456"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        userService.updateUserPassword(userId, "test@example.com", request);

        assertThat(PasswordUtils.matches("newPassword456", testUser.getPasswordHash())).isTrue();
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when current password is incorrect")
    void shouldThrowExceptionWhenCurrentPasswordIncorrect() {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                "wrongPassword",
                "newPassword456"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserPassword(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña actual es incorrecta");
    }

    @Test
    @DisplayName("Should throw exception when new password is empty")
    void shouldThrowExceptionWhenNewPasswordEmpty() {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                "password123",
                ""
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserPassword(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La nueva contraseña no puede estar vacía");
    }

    @Test
    @DisplayName("Should throw exception when current password is null")
    void shouldThrowExceptionWhenCurrentPasswordNull() {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                null,
                "newPassword456"
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.updateUserPassword(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña actual es requerida");
    }

    @Test
    @DisplayName("Should delete EMPLOYEE user when no open orders")
    void shouldDeleteEmployeeWhenNoOpenOrders() {
        testMembership.setRole(MembershipRole.EMPLOYEE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(saleRepository.existsByCreatedByUserIdAndOccurredAtIsNull(userId)).thenReturn(false);

        userService.deleteUser(userId, "test@example.com");

        verify(saleRepository).nullifyCreatedByUser(userId);
        verify(reservationRepository).nullifyCreatedByUser(userId);
        verify(userRepository).delete(testUser);
        verify(businessRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Should throw exception when EMPLOYEE has open orders")
    void shouldThrowWhenEmployeeHasOpenOrders() {
        testMembership.setRole(MembershipRole.EMPLOYEE);
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(saleRepository.existsByCreatedByUserIdAndOccurredAtIsNull(userId)).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteUser(userId, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debe cerrar sus órdenes abiertas antes de eliminar su usuario");

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should delete OWNER and cascade delete business and employees")
    void shouldDeleteOwnerAndCascadeDeleteBusinessAndEmployees() {
        testMembership.setRole(MembershipRole.OWNER);

        UUID empUserId = UUID.randomUUID();
        User empUser = User.builder()
                .id(empUserId)
                .email("employee@example.com")
                .name("Employee")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership empMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(empUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(membershipRepository.findByBusinessId(businessId)).thenReturn(List.of(testMembership, empMembership));

        userService.deleteUser(userId, "test@example.com");

        // Employee FK refs nullified and user deleted
        verify(saleRepository).nullifyCreatedByUser(empUserId);
        verify(reservationRepository).nullifyCreatedByUser(empUserId);
        verify(userRepository).delete(empUser);
        // Owner membership deleted before business
        verify(membershipRepository).delete(testMembership);
        // Business deleted
        verify(businessRepository).deleteById(businessId);
        // Owner deleted
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("Should delete OWNER with no employees")
    void shouldDeleteOwnerWithNoEmployees() {
        testMembership.setRole(MembershipRole.OWNER);

        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByUserId(userId)).thenReturn(List.of(testMembership));
        when(membershipRepository.findByBusinessId(businessId)).thenReturn(List.of(testMembership));

        userService.deleteUser(userId, "test@example.com");

        verify(membershipRepository).delete(testMembership);
        verify(businessRepository).deleteById(businessId);
        verify(userRepository).delete(testUser);
    }

    @Test
    @DisplayName("Should throw exception when unauthorized to delete user")
    void shouldThrowExceptionWhenUnauthorizedToDeleteUser() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));

        assertThatThrownBy(() -> userService.deleteUser(userId, "unauthorized@example.com"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No autorizado para borrar este usuario");
    }

    @Test
    @DisplayName("Should update membership status successfully")
    void shouldUpdateMembershipStatusSuccessfully() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder()
                .id(targetUserId)
                .email("target@example.com")
                .name("Target")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership targetMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(targetUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(membershipRepository.save(any(BusinessMembership.class))).thenReturn(targetMembership);

        userService.updateMembershipStatus(businessId, targetUserId, "test@example.com", request);

        assertThat(targetMembership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
        verify(membershipRepository).save(targetMembership);
    }

    @Test
    @DisplayName("Should throw exception when non-owner tries to update status")
    void shouldThrowExceptionWhenNonOwnerTriesToUpdateStatus() {
        testMembership.setRole(MembershipRole.EMPLOYEE);
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Solo los propietarios pueden actualizar el status de membresía");
    }

    @Test
    @DisplayName("Should throw exception when owner tries to update own status")
    void shouldThrowExceptionWhenOwnerTriesToUpdateOwnStatus() {
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes cambiar tu propio status de membresía");
    }

    @Test
    @DisplayName("Should promote employee to owner successfully")
    void shouldPromoteEmployeeToOwnerSuccessfully() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder()
                .id(targetUserId)
                .email("target@example.com")
                .name("Target")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership targetMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(targetUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.of(targetMembership));
        when(membershipRepository.save(any(BusinessMembership.class))).thenReturn(targetMembership);

        userService.updateMembershipRole(businessId, targetUserId, "test@example.com", request);

        assertThat(targetMembership.getRole()).isEqualTo(MembershipRole.OWNER);
        verify(membershipRepository).save(targetMembership);
    }

    @Test
    @DisplayName("Should throw exception when trying to change role from non-employee")
    void shouldThrowExceptionWhenTryingToChangeRoleFromNonEmployee() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder()
                .id(targetUserId)
                .email("target@example.com")
                .name("Target")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership targetMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(targetUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.EMPLOYEE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, targetUserId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se puede promocionar empleados a propietarios");
    }

    @Test
    @DisplayName("Should throw exception when trying to change own role")
    void shouldThrowExceptionWhenTryingToChangeOwnRole() {
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.EMPLOYEE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No puedes cambiar tu propio role de membresía");
    }

    @Test
    @DisplayName("Should throw exception when trying to set role other than OWNER")
    void shouldThrowExceptionWhenTryingToSetRoleOtherThanOwner() {
        UUID targetUserId = UUID.randomUUID();
        User targetUser = User.builder()
                .id(targetUserId)
                .email("target@example.com")
                .name("Target")
                .lastName("User")
                .passwordHash("hash")
                .build();

        BusinessMembership targetMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(targetUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.EMPLOYEE);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, targetUserId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se puede promocionar a propietario (OWNER)");
    }

    // Additional tests to cover missing lambda expressions

    @Test
    @DisplayName("Should throw exception when user not found in updateUserProfile")
    void shouldThrowExceptionWhenUserNotFoundInUpdateProfile() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest("new@example.com", "Name", "Last");
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserProfile(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when user not found in updateUserPassword")
    void shouldThrowExceptionWhenUserNotFoundInUpdatePassword() {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest("old", "new");
        
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserPassword(userId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when user not found in deleteUser")
    void shouldThrowExceptionWhenUserNotFoundInDelete() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.deleteUser(userId, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when requester not found in getUserById")
    void shouldThrowExceptionWhenRequesterNotFoundInGetUserById() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario solicitante no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when target user not found in getUserById")
    void shouldThrowExceptionWhenTargetUserNotFoundInGetUserById() {
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(userId, "test@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when requester not found in updateMembershipStatus")
    void shouldThrowExceptionWhenRequesterNotFoundInUpdateStatus() {
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario solicitante no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when requester has no access to business in updateMembershipStatus")
    void shouldThrowExceptionWhenRequesterHasNoAccessInUpdateStatus() {
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }

    @Test
    @DisplayName("Should throw exception when requester membership is inactive in updateMembershipStatus")
    void shouldThrowExceptionWhenRequesterInactiveInUpdateStatus() {
        testMembership.setStatus(MembershipStatus.INACTIVE);
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.ACTIVE);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
    }

    @Test
    @DisplayName("Should throw exception when target user is not member in updateMembershipStatus")
    void shouldThrowExceptionWhenTargetNotMemberInUpdateStatus() {
        UUID targetUserId = UUID.randomUUID();
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipStatus(businessId, targetUserId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario no es miembro de este negocio");
    }

    @Test
    @DisplayName("Should throw exception when requester not found in updateMembershipRole")
    void shouldThrowExceptionWhenRequesterNotFoundInUpdateRole() {
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario solicitante no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when requester has no access to business in updateMembershipRole")
    void shouldThrowExceptionWhenRequesterHasNoAccessInUpdateRole() {
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }

    @Test
    @DisplayName("Should throw exception when requester membership is inactive in updateMembershipRole")
    void shouldThrowExceptionWhenRequesterInactiveInUpdateRole() {
        testMembership.setStatus(MembershipStatus.INACTIVE);
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
    }

    @Test
    @DisplayName("Should throw exception when target user is not member in updateMembershipRole")
    void shouldThrowExceptionWhenTargetNotMemberInUpdateRole() {
        UUID targetUserId = UUID.randomUUID();
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, targetUserId, "test@example.com", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El usuario no es miembro de este negocio");
    }

    @Test
    @DisplayName("Should throw exception when non-owner tries to update role")
    void shouldThrowExceptionWhenNonOwnerTriesToUpdateRole() {
        testMembership.setRole(MembershipRole.EMPLOYEE);
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);
        
        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId)).thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> userService.updateMembershipRole(businessId, UUID.randomUUID(), "test@example.com", request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Solo los propietarios pueden actualizar el role de membresía");
    }
}
