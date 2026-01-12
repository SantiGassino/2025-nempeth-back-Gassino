package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.exception.AuthenticationException;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.*;
import com.nempeth.korven.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessMembershipRepository businessMembershipRepository;

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private Business testBusiness;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_NAME = "John";
    private static final String TEST_LASTNAME = "Doe";

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .lastName(TEST_LASTNAME)
                .passwordHash("$2a$10$hashedPassword")
                .build();

        testBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode("ABC12345")
                .joinCodeEnabled(true)
                .build();
    }

    // ==================== REGISTER TESTS ====================

    @Test
    @DisplayName("Should register new user successfully")
    void shouldRegisterNewUserSuccessfully() {
        // Given
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(UUID.randomUUID());
            return savedUser;
        });

        // When
        UUID userId = authService.register(request);

        // Then
        assertThat(userId).isNotNull();
        verify(userRepository).existsByEmail(TEST_EMAIL);
        verify(userRepository).save(any(User.class));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(savedUser.getName()).isEqualTo(TEST_NAME);
        assertThat(savedUser.getLastName()).isEqualTo(TEST_LASTNAME);
        assertThat(savedUser.getPasswordHash()).isNotNull();
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);
    }

    @Test
    @DisplayName("Should throw exception when email already exists during registration")
    void shouldThrowExceptionWhenEmailAlreadyExistsDuringRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email ya registrado");

        verify(userRepository).existsByEmail(TEST_EMAIL);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should hash password during registration")
    void shouldHashPasswordDuringRegistration() {
        // Given
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD);
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.register(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).startsWith("$2a$"); // BCrypt hash prefix
    }

    // ==================== LOGIN TESTS ====================

    @Test
    @DisplayName("Should login successfully with valid credentials")
    void shouldLoginSuccessfullyWithValidCredentials() {
        // Given
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        String expectedToken = "jwt.token.here";
        
        // Create a user with properly hashed password
        User userWithHashedPassword = User.builder()
                .id(testUser.getId())
                .email(TEST_EMAIL)
                .name(TEST_NAME)
                .lastName(TEST_LASTNAME)
                .passwordHash(com.nempeth.korven.utils.PasswordUtils.hash(TEST_PASSWORD))
                .build();

        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(userWithHashedPassword));
        when(jwtUtils.generateToken(anyString(), anyMap())).thenReturn(expectedToken);

        // When
        String token = authService.loginAndIssueToken(request);

        // Then
        assertThat(token).isEqualTo(expectedToken);
        verify(userRepository).findByEmailIgnoreCase(TEST_EMAIL);
        verify(jwtUtils).generateToken(eq(TEST_EMAIL), anyMap());
    }

    @Test
    @DisplayName("Should throw exception when user not found during login")
    void shouldThrowExceptionWhenUserNotFoundDuringLogin() {
        // Given
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.loginAndIssueToken(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Credenciales inválidas");

        verify(userRepository).findByEmailIgnoreCase(TEST_EMAIL);
        verify(jwtUtils, never()).generateToken(anyString(), anyMap());
    }

    @Test
    @DisplayName("Should throw exception when password is incorrect")
    void shouldThrowExceptionWhenPasswordIsIncorrect() {
        // Given
        LoginRequest request = new LoginRequest(TEST_EMAIL, "wrongPassword");
        User userWithHashedPassword = User.builder()
                .id(testUser.getId())
                .email(TEST_EMAIL)
                .passwordHash(com.nempeth.korven.utils.PasswordUtils.hash(TEST_PASSWORD))
                .build();

        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(userWithHashedPassword));

        // When / Then
        assertThatThrownBy(() -> authService.loginAndIssueToken(request))
                .isInstanceOf(AuthenticationException.class)
                .hasMessage("Credenciales inválidas");

        verify(userRepository).findByEmailIgnoreCase(TEST_EMAIL);
        verify(jwtUtils, never()).generateToken(anyString(), anyMap());
    }

    @Test
    @DisplayName("Should include userId in JWT claims")
    void shouldIncludeUserIdInJwtClaims() {
        // Given
        LoginRequest request = new LoginRequest(TEST_EMAIL, TEST_PASSWORD);
        User userWithHashedPassword = User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .passwordHash(com.nempeth.korven.utils.PasswordUtils.hash(TEST_PASSWORD))
                .build();

        when(userRepository.findByEmailIgnoreCase(TEST_EMAIL)).thenReturn(Optional.of(userWithHashedPassword));
        when(jwtUtils.generateToken(anyString(), anyMap())).thenReturn("token");

        // When
        authService.loginAndIssueToken(request);

        // Then
        ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(jwtUtils).generateToken(eq(TEST_EMAIL), claimsCaptor.capture());
        Map<String, Object> claims = claimsCaptor.getValue();
        assertThat(claims).containsKey("userId");
        assertThat(claims.get("userId")).isEqualTo(userWithHashedPassword.getId().toString());
    }

    @Test
    @DisplayName("Should perform case-insensitive email lookup during login")
    void shouldPerformCaseInsensitiveEmailLookupDuringLogin() {
        // Given
        String upperCaseEmail = "TEST@EXAMPLE.COM";
        LoginRequest request = new LoginRequest(upperCaseEmail, TEST_PASSWORD);
        User userWithHashedPassword = User.builder()
                .id(UUID.randomUUID())
                .email(TEST_EMAIL)
                .passwordHash(com.nempeth.korven.utils.PasswordUtils.hash(TEST_PASSWORD))
                .build();

        when(userRepository.findByEmailIgnoreCase(upperCaseEmail)).thenReturn(Optional.of(userWithHashedPassword));
        when(jwtUtils.generateToken(anyString(), anyMap())).thenReturn("token");

        // When
        authService.loginAndIssueToken(request);

        // Then
        verify(userRepository).findByEmailIgnoreCase(upperCaseEmail);
    }

    // ==================== REGISTER OWNER TESTS ====================

    @Test
    @DisplayName("Should register owner with business successfully")
    void shouldRegisterOwnerWithBusinessSuccessfully() {
        // Given
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "My Business"
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(UUID.randomUUID());
            return business;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RegistrationResponse response = authService.registerOwner(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isNotNull();
        assertThat(response.message()).contains("Propietario y negocio registrados exitosamente");
        assertThat(response.business()).isNotNull();
        assertThat(response.business().name()).isEqualTo("My Business");
        assertThat(response.business().joinCode()).isNotNull();
        assertThat(response.business().joinCode()).hasSize(8);
        assertThat(response.business().joinCodeEnabled()).isTrue();

        verify(userRepository).save(any(User.class));
        verify(businessRepository).save(any(Business.class));
        verify(businessMembershipRepository).save(any(BusinessMembership.class));
    }

    @Test
    @DisplayName("Should create business membership with OWNER role")
    void shouldCreateBusinessMembershipWithOwnerRole() {
        // Given
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "My Business"
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(UUID.randomUUID());
            return business;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerOwner(request);

        // Then
        ArgumentCaptor<BusinessMembership> membershipCaptor = ArgumentCaptor.forClass(BusinessMembership.class);
        verify(businessMembershipRepository).save(membershipCaptor.capture());
        BusinessMembership savedMembership = membershipCaptor.getValue();
        
        assertThat(savedMembership.getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should generate 8-character join code for business")
    void shouldGenerate8CharacterJoinCodeForBusiness() {
        // Given
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "My Business"
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(UUID.randomUUID());
            return business;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerOwner(request);

        // Then
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(businessCaptor.capture());
        Business savedBusiness = businessCaptor.getValue();
        
        assertThat(savedBusiness.getJoinCode()).hasSize(8);
        assertThat(savedBusiness.getJoinCode()).matches("[A-Z0-9]{8}");
    }

    @Test
    @DisplayName("Should throw exception when owner email already exists")
    void shouldThrowExceptionWhenOwnerEmailAlreadyExists() {
        // Given
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "My Business"
        );
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.registerOwner(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email ya registrado");

        verify(userRepository).existsByEmail(TEST_EMAIL);
        verify(userRepository, never()).save(any(User.class));
        verify(businessRepository, never()).save(any(Business.class));
    }

    @Test
    @DisplayName("Should enable join code by default when creating business")
    void shouldEnableJoinCodeByDefaultWhenCreatingBusiness() {
        // Given
        RegisterOwnerRequest request = new RegisterOwnerRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "My Business"
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessRepository.save(any(Business.class))).thenAnswer(invocation -> {
            Business business = invocation.getArgument(0);
            business.setId(UUID.randomUUID());
            return business;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerOwner(request);

        // Then
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(businessCaptor.capture());
        Business savedBusiness = businessCaptor.getValue();
        
        assertThat(savedBusiness.getJoinCodeEnabled()).isTrue();
    }

    // ==================== REGISTER EMPLOYEE TESTS ====================

    @Test
    @DisplayName("Should register employee successfully with valid join code")
    void shouldRegisterEmployeeSuccessfullyWithValidJoinCode() {
        // Given
        String joinCode = "ABC12345";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, joinCode
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(joinCode)).thenReturn(Optional.of(testBusiness));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        RegistrationResponse response = authService.registerEmployee(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isNotNull();
        assertThat(response.message()).contains("Empleado registrado y agregado al negocio exitosamente");
        assertThat(response.business()).isNotNull();
        assertThat(response.business().name()).isEqualTo(testBusiness.getName());

        verify(businessRepository).findByJoinCode(joinCode);
        verify(userRepository).save(any(User.class));
        verify(businessMembershipRepository).save(any(BusinessMembership.class));
    }

    @Test
    @DisplayName("Should create membership with EMPLOYEE role and PENDING status")
    void shouldCreateMembershipWithEmployeeRoleAndPendingStatus() {
        // Given
        String joinCode = "ABC12345";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, joinCode
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(joinCode)).thenReturn(Optional.of(testBusiness));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerEmployee(request);

        // Then
        ArgumentCaptor<BusinessMembership> membershipCaptor = ArgumentCaptor.forClass(BusinessMembership.class);
        verify(businessMembershipRepository).save(membershipCaptor.capture());
        BusinessMembership savedMembership = membershipCaptor.getValue();
        
        assertThat(savedMembership.getRole()).isEqualTo(MembershipRole.EMPLOYEE);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw exception when employee email already exists")
    void shouldThrowExceptionWhenEmployeeEmailAlreadyExists() {
        // Given
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, "ABC12345"
        );
        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> authService.registerEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email ya registrado");

        verify(userRepository).existsByEmail(TEST_EMAIL);
        verify(businessRepository, never()).findByJoinCode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when join code is invalid")
    void shouldThrowExceptionWhenJoinCodeIsInvalid() {
        // Given
        String invalidJoinCode = "INVALID1";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, invalidJoinCode
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(invalidJoinCode)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authService.registerEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código de negocio inválido");

        verify(businessRepository).findByJoinCode(invalidJoinCode);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw exception when join code is disabled")
    void shouldThrowExceptionWhenJoinCodeIsDisabled() {
        // Given
        String joinCode = "ABC12345";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, joinCode
        );

        Business businessWithDisabledCode = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode(joinCode)
                .joinCodeEnabled(false)
                .build();

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(joinCode)).thenReturn(Optional.of(businessWithDisabledCode));

        // When / Then
        assertThatThrownBy(() -> authService.registerEmployee(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El negocio no está aceptando nuevos empleados");

        verify(businessRepository).findByJoinCode(joinCode);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should link employee to correct business")
    void shouldLinkEmployeeToCorrectBusiness() {
        // Given
        String joinCode = "ABC12345";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, joinCode
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(joinCode)).thenReturn(Optional.of(testBusiness));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerEmployee(request);

        // Then
        ArgumentCaptor<BusinessMembership> membershipCaptor = ArgumentCaptor.forClass(BusinessMembership.class);
        verify(businessMembershipRepository).save(membershipCaptor.capture());
        BusinessMembership savedMembership = membershipCaptor.getValue();
        
        assertThat(savedMembership.getBusiness()).isEqualTo(testBusiness);
        assertThat(savedMembership.getUser()).isNotNull();
    }

    @Test
    @DisplayName("Should hash employee password during registration")
    void shouldHashEmployeePasswordDuringRegistration() {
        // Given
        String joinCode = "ABC12345";
        RegisterEmployeeRequest request = new RegisterEmployeeRequest(
                TEST_EMAIL, TEST_NAME, TEST_LASTNAME, TEST_PASSWORD, joinCode
        );

        when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
        when(businessRepository.findByJoinCode(joinCode)).thenReturn(Optional.of(testBusiness));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(businessMembershipRepository.save(any(BusinessMembership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        authService.registerEmployee(request);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        
        assertThat(savedUser.getPasswordHash()).startsWith("$2a$"); // BCrypt hash prefix
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(TEST_PASSWORD);
    }
}
