package com.nempeth.korven.service;

import com.nempeth.korven.constants.CategoryType;
import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BusinessServiceTest {

    @Mock
    private BusinessRepository businessRepository;
    
    @Mock
    private BusinessMembershipRepository membershipRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private CategoryRepository categoryRepository;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private SaleRepository saleRepository;
    
    @InjectMocks
    private BusinessService businessService;
    
    private User testUser;
    private Business testBusiness;
    private BusinessMembership ownerMembership;
    private UUID userId;
    private UUID businessId;
    private String userEmail;
    
    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        userEmail = "test@example.com";
        
        testUser = User.builder()
                .id(userId)
                .email(userEmail)
                .name("John")
                .lastName("Doe")
                .build();
        
        testBusiness = Business.builder()
                .id(businessId)
                .name("Test Business")
                .joinCode("ABC12345")
                .joinCodeEnabled(true)
                .build();
        
        ownerMembership = BusinessMembership.builder()
                .user(testUser)
                .business(testBusiness)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }
    
    // ==================== CREATE BUSINESS TESTS ====================
    
    @Test
    void createBusiness_shouldCreateBusinessAndOwnerMembership_whenValidRequest() {
        // Given
        CreateBusinessRequest request = new CreateBusinessRequest("My New Business");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.existsByJoinCode(anyString())).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(testBusiness);
        when(membershipRepository.save(any(BusinessMembership.class)))
                .thenReturn(ownerMembership);
        
        // When
        BusinessResponse response = businessService.createBusiness(userEmail, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(businessId);
        assertThat(response.name()).isEqualTo("Test Business");
        assertThat(response.joinCode()).isEqualTo("ABC12345");
        assertThat(response.joinCodeEnabled()).isTrue();
        
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(businessCaptor.capture());
        
        Business capturedBusiness = businessCaptor.getValue();
        assertThat(capturedBusiness.getName()).isEqualTo("My New Business");
        assertThat(capturedBusiness.getJoinCode()).isNotNull().hasSize(8);
        assertThat(capturedBusiness.getJoinCodeEnabled()).isTrue();
        
        ArgumentCaptor<BusinessMembership> membershipCaptor = ArgumentCaptor.forClass(BusinessMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        
        BusinessMembership capturedMembership = membershipCaptor.getValue();
        assertThat(capturedMembership.getUser()).isEqualTo(testUser);
        assertThat(capturedMembership.getBusiness()).isEqualTo(testBusiness);
        assertThat(capturedMembership.getRole()).isEqualTo(MembershipRole.OWNER);
        assertThat(capturedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }
    
    @Test
    void createBusiness_shouldGenerateUniqueJoinCode() {
        // Given
        CreateBusinessRequest request = new CreateBusinessRequest("Business");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.existsByJoinCode(anyString()))
                .thenReturn(true)  // First attempt exists
                .thenReturn(false); // Second attempt is unique
        when(businessRepository.save(any(Business.class))).thenReturn(testBusiness);
        when(membershipRepository.save(any(BusinessMembership.class)))
                .thenReturn(ownerMembership);
        
        // When
        businessService.createBusiness(userEmail, request);
        
        // Then
        verify(businessRepository, atLeast(2)).existsByJoinCode(anyString());
    }
    
    @Test
    void createBusiness_shouldGenerateJoinCodeWithCorrectFormat() {
        // Given
        CreateBusinessRequest request = new CreateBusinessRequest("Business");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.existsByJoinCode(anyString())).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(testBusiness);
        when(membershipRepository.save(any(BusinessMembership.class)))
                .thenReturn(ownerMembership);
        
        // When
        businessService.createBusiness(userEmail, request);
        
        // Then
        ArgumentCaptor<Business> businessCaptor = ArgumentCaptor.forClass(Business.class);
        verify(businessRepository).save(businessCaptor.capture());
        
        Business capturedBusiness = businessCaptor.getValue();
        String joinCode = capturedBusiness.getJoinCode();
        
        assertThat(joinCode).hasSize(8);
        assertThat(joinCode).matches("[A-Z0-9]{8}");
    }
    
    @Test
    void createBusiness_shouldThrowException_whenUserNotFound() {
        // Given
        CreateBusinessRequest request = new CreateBusinessRequest("Business");
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.createBusiness(userEmail, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
        
        verify(businessRepository, never()).save(any());
    }
    
    @Test
    void createBusiness_shouldHandleCaseInsensitiveEmail() {
        // Given
        String upperEmail = "TEST@EXAMPLE.COM";
        CreateBusinessRequest request = new CreateBusinessRequest("Business");
        
        when(userRepository.findByEmailIgnoreCase(upperEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.existsByJoinCode(anyString())).thenReturn(false);
        when(businessRepository.save(any(Business.class))).thenReturn(testBusiness);
        when(membershipRepository.save(any(BusinessMembership.class)))
                .thenReturn(ownerMembership);
        
        // When
        businessService.createBusiness(upperEmail, request);
        
        // Then
        verify(userRepository).findByEmailIgnoreCase(upperEmail);
    }
    
    // ==================== JOIN BUSINESS TESTS ====================
    
    @Test
    void joinBusiness_shouldCreateEmployeeMembership_whenValidJoinCode() {
        // Given
        JoinBusinessRequest request = new JoinBusinessRequest("ABC12345");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByJoinCode("ABC12345")).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.existsByBusinessIdAndUserId(businessId, userId)).thenReturn(false);
        when(membershipRepository.save(any(BusinessMembership.class)))
                .thenReturn(ownerMembership);
        
        // When
        BusinessResponse response = businessService.joinBusiness(userEmail, request);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(businessId);
        assertThat(response.name()).isEqualTo("Test Business");
        
        ArgumentCaptor<BusinessMembership> membershipCaptor = ArgumentCaptor.forClass(BusinessMembership.class);
        verify(membershipRepository).save(membershipCaptor.capture());
        
        BusinessMembership capturedMembership = membershipCaptor.getValue();
        assertThat(capturedMembership.getUser()).isEqualTo(testUser);
        assertThat(capturedMembership.getBusiness()).isEqualTo(testBusiness);
        assertThat(capturedMembership.getRole()).isEqualTo(MembershipRole.EMPLOYEE);
        assertThat(capturedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }
    
    @Test
    void joinBusiness_shouldThrowException_whenUserNotFound() {
        // Given
        JoinBusinessRequest request = new JoinBusinessRequest("ABC12345");
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.joinBusiness(userEmail, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
        
        verify(membershipRepository, never()).save(any());
    }
    
    @Test
    void joinBusiness_shouldThrowException_whenInvalidJoinCode() {
        // Given
        JoinBusinessRequest request = new JoinBusinessRequest("INVALID");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByJoinCode("INVALID")).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.joinBusiness(userEmail, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Código de acceso inválido");
        
        verify(membershipRepository, never()).save(any());
    }
    
    @Test
    void joinBusiness_shouldThrowException_whenJoinCodeDisabled() {
        // Given
        testBusiness.setJoinCodeEnabled(false);
        JoinBusinessRequest request = new JoinBusinessRequest("ABC12345");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByJoinCode("ABC12345")).thenReturn(Optional.of(testBusiness));
        
        // When/Then
        assertThatThrownBy(() -> businessService.joinBusiness(userEmail, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El código de acceso está deshabilitado");
        
        verify(membershipRepository, never()).save(any());
    }
    
    @Test
    void joinBusiness_shouldThrowException_whenAlreadyMember() {
        // Given
        JoinBusinessRequest request = new JoinBusinessRequest("ABC12345");
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(businessRepository.findByJoinCode("ABC12345")).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.existsByBusinessIdAndUserId(businessId, userId)).thenReturn(true);
        
        // When/Then
        assertThatThrownBy(() -> businessService.joinBusiness(userEmail, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya eres miembro de este negocio");
        
        verify(membershipRepository, never()).save(any());
    }
    
    // ==================== GET BUSINESS DETAIL TESTS ====================
    
    @Test
    void getBusinessDetail_shouldReturnCompleteBusinessInfo_whenValidAccess() {
        // Given
        Category category = Category.builder()
                .id(UUID.randomUUID())
                .name("Drinks")
                .type(CategoryType.STATIC)
                .displayName("Bebidas")
                .icon("drink-icon")
                .business(testBusiness)
                .build();
        
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Beer")
                .description("Cold beer")
                .price(new BigDecimal("5.00"))
                .cost(new BigDecimal("2.00"))
                .category(category)
                .business(testBusiness)
                .build();
        
        Sale sale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .totalAmount(new BigDecimal("100.00"))
                .occurredAt(OffsetDateTime.now())
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership));
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of(category));
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of(product));
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of(sale));
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(businessId);
        assertThat(response.name()).isEqualTo("Test Business");
        assertThat(response.joinCode()).isEqualTo("ABC12345");
        assertThat(response.joinCodeEnabled()).isTrue();
        
        assertThat(response.members()).hasSize(1);
        assertThat(response.members().get(0).userId()).isEqualTo(userId);
        assertThat(response.members().get(0).role()).isEqualTo(MembershipRole.OWNER);
        
        assertThat(response.categories()).hasSize(1);
        assertThat(response.categories().get(0).name()).isEqualTo("Drinks");
        
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).name()).isEqualTo("Beer");
        
        assertThat(response.stats()).isNotNull();
        assertThat(response.stats().totalMembers()).isEqualTo(1L);
        assertThat(response.stats().totalCategories()).isEqualTo(1L);
        assertThat(response.stats().totalProducts()).isEqualTo(1L);
        assertThat(response.stats().totalSales()).isEqualTo(1L);
        assertThat(response.stats().totalRevenue()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
    
    @Test
    void getBusinessDetail_shouldThrowException_whenUserNotFound() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessDetail(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }
    
    @Test
    void getBusinessDetail_shouldThrowException_whenNoBusinessAccess() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessDetail(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }
    
    @Test
    void getBusinessDetail_shouldThrowException_whenMembershipInactive() {
        // Given
        ownerMembership.setStatus(MembershipStatus.INACTIVE);
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessDetail(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
    }
    
    @Test
    void getBusinessDetail_shouldThrowException_whenBusinessNotFound() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessDetail(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Negocio no encontrado");
    }
    
    @Test
    void getBusinessDetail_shouldCalculateStatsCorrectly_withMultipleSales() {
        // Given
        Sale sale1 = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .totalAmount(new BigDecimal("100.00"))
                .occurredAt(OffsetDateTime.now())
                .build();
        
        Sale sale2 = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .totalAmount(new BigDecimal("250.50"))
                .occurredAt(OffsetDateTime.now())
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership));
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of(sale1, sale2));
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response.stats().totalSales()).isEqualTo(2L);
        assertThat(response.stats().totalRevenue()).isEqualByComparingTo(new BigDecimal("350.50"));
    }
    
    @Test
    void getBusinessDetail_shouldHandleEmptyCollections() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of());
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response.members()).isEmpty();
        assertThat(response.categories()).isEmpty();
        assertThat(response.products()).isEmpty();
        assertThat(response.stats().totalMembers()).isZero();
        assertThat(response.stats().totalCategories()).isZero();
        assertThat(response.stats().totalProducts()).isZero();
        assertThat(response.stats().totalSales()).isZero();
        assertThat(response.stats().totalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }
    
    @Test
    void getBusinessDetail_shouldMapMemberDetailsCorrectly() {
        // Given
        User employee = User.builder()
                .id(UUID.randomUUID())
                .email("employee@example.com")
                .name("Jane")
                .lastName("Smith")
                .build();
        
        BusinessMembership employeeMembership = BusinessMembership.builder()
                .user(employee)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership, employeeMembership));
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of());
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response.members()).hasSize(2);
        
        BusinessMemberDetailResponse owner = response.members().stream()
                .filter(m -> m.role() == MembershipRole.OWNER)
                .findFirst()
                .orElseThrow();
        assertThat(owner.userName()).isEqualTo("John");
        assertThat(owner.userLastName()).isEqualTo("Doe");
        
        BusinessMemberDetailResponse emp = response.members().stream()
                .filter(m -> m.role() == MembershipRole.EMPLOYEE)
                .findFirst()
                .orElseThrow();
        assertThat(emp.userName()).isEqualTo("Jane");
        assertThat(emp.userLastName()).isEqualTo("Smith");
    }
    
    @Test
    void getBusinessDetail_shouldMapCategoryDetailsCorrectly() {
        // Given
        Category category = Category.builder()
                .id(UUID.randomUUID())
                .name("beverages")
                .type(CategoryType.STATIC)
                .displayName("Bebidas")
                .icon("drink-icon")
                .business(testBusiness)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of(category));
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of());
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response.categories()).hasSize(1);
        CategoryResponse catResponse = response.categories().get(0);
        assertThat(catResponse.name()).isEqualTo("beverages");
        assertThat(catResponse.type()).isEqualTo(CategoryType.STATIC);
        assertThat(catResponse.displayName()).isEqualTo("Bebidas");
        assertThat(catResponse.icon()).isEqualTo("drink-icon");
    }
    
    @Test
    void getBusinessDetail_shouldMapProductDetailsCorrectly() {
        // Given
        Category category = Category.builder()
                .id(UUID.randomUUID())
                .name("drinks")
                .type(CategoryType.STATIC)
                .displayName("Bebidas")
                .icon("icon")
                .business(testBusiness)
                .build();
        
        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Coca Cola")
                .description("Refreshing soda")
                .price(new BigDecimal("3.50"))
                .cost(new BigDecimal("1.50"))
                .category(category)
                .business(testBusiness)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(membershipRepository.findByBusinessIdAndStatus(businessId, MembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of());
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of(product));
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of());
        
        // When
        BusinessDetailResponse response = businessService.getBusinessDetail(userEmail, businessId);
        
        // Then
        assertThat(response.products()).hasSize(1);
        ProductResponse prodResponse = response.products().get(0);
        assertThat(prodResponse.name()).isEqualTo("Coca Cola");
        assertThat(prodResponse.description()).isEqualTo("Refreshing soda");
        assertThat(prodResponse.price()).isEqualByComparingTo(new BigDecimal("3.50"));
        assertThat(prodResponse.category().name()).isEqualTo("drinks");
    }
    
    // ==================== GET BUSINESS MEMBERS TESTS ====================
    
    @Test
    void getBusinessMembers_shouldReturnAllMembers_whenValidAccess() {
        // Given
        User employee = User.builder()
                .id(UUID.randomUUID())
                .email("employee@example.com")
                .name("Jane")
                .lastName("Smith")
                .build();
        
        BusinessMembership employeeMembership = BusinessMembership.builder()
                .user(employee)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ownerMembership, employeeMembership));
        
        // When
        List<BusinessMemberDetailResponse> members = businessService.getBusinessMembers(userEmail, businessId);
        
        // Then
        assertThat(members).hasSize(2);
        assertThat(members).anyMatch(m -> m.role() == MembershipRole.OWNER);
        assertThat(members).anyMatch(m -> m.role() == MembershipRole.EMPLOYEE);
    }
    
    @Test
    void getBusinessMembers_shouldIncludeInactiveMembers() {
        // Given
        User inactiveUser = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .name("Inactive")
                .lastName("User")
                .build();
        
        BusinessMembership inactiveMembership = BusinessMembership.builder()
                .user(inactiveUser)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.INACTIVE)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ownerMembership, inactiveMembership));
        
        // When
        List<BusinessMemberDetailResponse> members = businessService.getBusinessMembers(userEmail, businessId);
        
        // Then
        assertThat(members).hasSize(2);
        assertThat(members).anyMatch(m -> m.status() == MembershipStatus.INACTIVE);
    }
    
    @Test
    void getBusinessMembers_shouldThrowException_whenNoAccess() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessMembers(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }
    
    @Test
    void getBusinessMembers_shouldReturnEmptyList_whenNoMembers() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId)).thenReturn(List.of());
        
        // When
        List<BusinessMemberDetailResponse> members = businessService.getBusinessMembers(userEmail, businessId);
        
        // Then
        assertThat(members).isEmpty();
    }
    
    // ==================== GET BUSINESS EMPLOYEES TESTS ====================
    
    @Test
    void getBusinessEmployees_shouldReturnOnlyEmployees() {
        // Given
        User employee1 = User.builder()
                .id(UUID.randomUUID())
                .email("employee1@example.com")
                .name("Jane")
                .lastName("Smith")
                .build();
        
        User employee2 = User.builder()
                .id(UUID.randomUUID())
                .email("employee2@example.com")
                .name("Bob")
                .lastName("Jones")
                .build();
        
        BusinessMembership emp1Membership = BusinessMembership.builder()
                .user(employee1)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();
        
        BusinessMembership emp2Membership = BusinessMembership.builder()
                .user(employee2)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.INACTIVE)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ownerMembership, emp1Membership, emp2Membership));
        
        // When
        List<BusinessMemberDetailResponse> employees = businessService.getBusinessEmployees(userEmail, businessId);
        
        // Then
        assertThat(employees).hasSize(2);
        assertThat(employees).allMatch(m -> m.role() == MembershipRole.EMPLOYEE);
        assertThat(employees).noneMatch(m -> m.role() == MembershipRole.OWNER);
    }
    
    @Test
    void getBusinessEmployees_shouldIncludeBothActiveAndInactiveEmployees() {
        // Given
        User activeEmployee = User.builder()
                .id(UUID.randomUUID())
                .email("active@example.com")
                .name("Active")
                .lastName("Employee")
                .build();
        
        User inactiveEmployee = User.builder()
                .id(UUID.randomUUID())
                .email("inactive@example.com")
                .name("Inactive")
                .lastName("Employee")
                .build();
        
        BusinessMembership activeMembership = BusinessMembership.builder()
                .user(activeEmployee)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();
        
        BusinessMembership inactiveMembership = BusinessMembership.builder()
                .user(inactiveEmployee)
                .business(testBusiness)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.INACTIVE)
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ownerMembership, activeMembership, inactiveMembership));
        
        // When
        List<BusinessMemberDetailResponse> employees = businessService.getBusinessEmployees(userEmail, businessId);
        
        // Then
        assertThat(employees).hasSize(2);
        assertThat(employees).anyMatch(m -> m.status() == MembershipStatus.ACTIVE);
        assertThat(employees).anyMatch(m -> m.status() == MembershipStatus.INACTIVE);
    }
    
    @Test
    void getBusinessEmployees_shouldReturnEmptyList_whenNoEmployees() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(ownerMembership));
        when(membershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(ownerMembership));
        
        // When
        List<BusinessMemberDetailResponse> employees = businessService.getBusinessEmployees(userEmail, businessId);
        
        // Then
        assertThat(employees).isEmpty();
    }
    
    @Test
    void getBusinessEmployees_shouldThrowException_whenNoAccess() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> businessService.getBusinessEmployees(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }
}
