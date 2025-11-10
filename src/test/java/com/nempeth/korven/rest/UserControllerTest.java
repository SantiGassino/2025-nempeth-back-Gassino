package com.nempeth.korven.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.rest.dto.*;
import com.nempeth.korven.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@ActiveProfiles("test")
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private UUID userId;
    private UUID businessId;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();

        BusinessMembershipResponse membership = BusinessMembershipResponse.builder()
                .businessId(businessId)
                .businessName("Test Business")
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        userResponse = UserResponse.builder()
                .id(userId)
                .email("test@example.com")
                .name("Test")
                .lastName("User")
                .businesses(List.of(membership))
                .build();
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should get current user successfully")
    void shouldGetCurrentUserSuccessfully() throws Exception {
        when(userService.getUserByEmail("test@example.com")).thenReturn(userResponse);
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("Test"))
                .andExpect(jsonPath("$.lastName").value("User"))
                .andExpect(jsonPath("$.businesses").isArray())
                .andExpect(jsonPath("$.businesses[0].businessName").value("Test Business"));

        verify(userService).getUserByEmail("test@example.com");
    }

    @Test
    @DisplayName("Should return 401 when not authenticated")
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should get user by ID successfully")
    void shouldGetUserByIdSuccessfully() throws Exception {
        when(userService.getUserById(userId, "test@example.com")).thenReturn(userResponse);

        mockMvc.perform(get("/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("test@example.com"));

        verify(userService).getUserById(userId, "test@example.com");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when access denied to get user by ID")
    void shouldReturn403WhenAccessDeniedToGetUserById() throws Exception {
        when(userService.getUserById(any(UUID.class), eq("test@example.com")))
                .thenThrow(new AccessDeniedException("No tienes acceso"));

        mockMvc.perform(get("/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update profile successfully without email change")
    void shouldUpdateProfileSuccessfullyWithoutEmailChange() throws Exception {
        // Given
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "test@example.com",
                "NewName",
                "NewLastName"
        );

        when(userService.updateUserProfile(eq(userId), eq("test@example.com"), any(UpdateUserProfileRequest.class)))
                .thenReturn(false);

        mockMvc.perform(put("/users/{userId}/profile", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Usuario actualizado"))
                .andExpect(jsonPath("$.emailChanged").value(false));

        verify(userService).updateUserProfile(eq(userId), eq("test@example.com"), any(UpdateUserProfileRequest.class));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update profile with email change")
    void shouldUpdateProfileWithEmailChange() throws Exception {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "newemail@example.com",
                "NewName",
                "NewLastName"
        );

        when(userService.updateUserProfile(eq(userId), eq("test@example.com"), any(UpdateUserProfileRequest.class)))
                .thenReturn(true);

        mockMvc.perform(put("/users/{userId}/profile", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Usuario actualizado. Reingresá con el nuevo email para obtener un nuevo token."))
                .andExpect(jsonPath("$.emailChanged").value(true));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when unauthorized to update profile")
    void shouldReturn403WhenUnauthorizedToUpdateProfile() throws Exception {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest(
                "newemail@example.com",
                "NewName",
                "NewLastName"
        );

        when(userService.updateUserProfile(any(UUID.class), eq("test@example.com"), any(UpdateUserProfileRequest.class)))
                .thenThrow(new AccessDeniedException("No autorizado"));

        mockMvc.perform(put("/users/{userId}/profile", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update password successfully")
    void shouldUpdatePasswordSuccessfully() throws Exception {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                "currentPassword",
                "newPassword123"
        );

        doNothing().when(userService).updateUserPassword(eq(userId), eq("test@example.com"), any(UpdateUserPasswordRequest.class));

        mockMvc.perform(put("/users/{userId}/password", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contraseña actualizada"));

        verify(userService).updateUserPassword(eq(userId), eq("test@example.com"), any(UpdateUserPasswordRequest.class));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 when current password is incorrect")
    void shouldReturn400WhenCurrentPasswordIncorrect() throws Exception {
        UpdateUserPasswordRequest request = new UpdateUserPasswordRequest(
                "wrongPassword",
                "newPassword123"
        );

        doThrow(new IllegalArgumentException("La contraseña actual es incorrecta"))
                .when(userService).updateUserPassword(any(UUID.class), eq("test@example.com"), any(UpdateUserPasswordRequest.class));

        mockMvc.perform(put("/users/{userId}/password", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update membership status successfully")
    void shouldUpdateMembershipStatusSuccessfully() throws Exception {
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);

        doNothing().when(userService).updateMembershipStatus(
                eq(businessId),
                eq(userId),
                eq("test@example.com"),
                any(UpdateMembershipStatusRequest.class)
        );

        mockMvc.perform(put("/users/businesses/{businessId}/members/{userId}/status", businessId, userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Status de membresía actualizado"));

        verify(userService).updateMembershipStatus(
                eq(businessId),
                eq(userId),
                eq("test@example.com"),
                any(UpdateMembershipStatusRequest.class)
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when non-owner tries to update status")
    void shouldReturn403WhenNonOwnerTriesToUpdateStatus() throws Exception {
        UpdateMembershipStatusRequest request = new UpdateMembershipStatusRequest(MembershipStatus.INACTIVE);

        doThrow(new AccessDeniedException("Solo los propietarios pueden actualizar"))
                .when(userService).updateMembershipStatus(
                        any(UUID.class),
                        any(UUID.class),
                        eq("test@example.com"),
                        any(UpdateMembershipStatusRequest.class)
                );

        mockMvc.perform(put("/users/businesses/{businessId}/members/{userId}/status", businessId, userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update membership role successfully")
    void shouldUpdateMembershipRoleSuccessfully() throws Exception {
        UpdateMembershipRoleRequest request = new UpdateMembershipRoleRequest(MembershipRole.OWNER);

        doNothing().when(userService).updateMembershipRole(
                eq(businessId),
                eq(userId),
                eq("test@example.com"),
                any(UpdateMembershipRoleRequest.class)
        );

        mockMvc.perform(put("/users/businesses/{businessId}/members/{userId}/role", businessId, userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Role de membresía actualizado"));

        verify(userService).updateMembershipRole(
                eq(businessId),
                eq(userId),
                eq("test@example.com"),
                any(UpdateMembershipRoleRequest.class)
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 when validation fails for role update")
    void shouldReturn400WhenValidationFailsForRoleUpdate() throws Exception {
        String invalidRequest = "{}";

        mockMvc.perform(put("/users/businesses/{businessId}/members/{userId}/role", businessId, userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());

        verify(userService, never()).updateMembershipRole(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                any(UpdateMembershipRoleRequest.class)
        );
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should delete user successfully")
    void shouldDeleteUserSuccessfully() throws Exception {
        doNothing().when(userService).deleteUser(userId, "test@example.com");

        mockMvc.perform(delete("/users/{userId}", userId)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Usuario eliminado"));

        verify(userService).deleteUser(userId, "test@example.com");
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 403 when unauthorized to delete user")
    void shouldReturn403WhenUnauthorizedToDeleteUser() throws Exception {
        doThrow(new AccessDeniedException("No autorizado"))
                .when(userService).deleteUser(any(UUID.class), eq("test@example.com"));

        mockMvc.perform(delete("/users/{userId}", userId)
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 400 when user not found for deletion")
    void shouldReturn400WhenUserNotFoundForDeletion() throws Exception {
        doThrow(new IllegalArgumentException("Usuario no encontrado"))
                .when(userService).deleteUser(any(UUID.class), eq("test@example.com"));

        mockMvc.perform(delete("/users/{userId}", userId)
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
