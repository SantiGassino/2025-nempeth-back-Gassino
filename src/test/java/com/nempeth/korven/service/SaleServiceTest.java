package com.nempeth.korven.service;

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
class SaleServiceTest {

    @Mock
    private SaleRepository saleRepository;
    
    @Mock
    private SaleItemRepository saleItemRepository;
    
    @Mock
    private ProductRepository productRepository;
    
    @Mock
    private BusinessRepository businessRepository;
    
    @Mock
    private BusinessMembershipRepository membershipRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private SaleService saleService;
    
    private User testUser;
    private Business testBusiness;
    private BusinessMembership activeMembership;
    private Product testProduct;
    private Category testCategory;
    private UUID businessId;
    private UUID userId;
    private UUID productId;
    private String userEmail;
    
    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
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
                .build();
        
        activeMembership = BusinessMembership.builder()
                .user(testUser)
                .business(testBusiness)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
        
        testCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Test Category")
                .business(testBusiness)
                .build();
        
        testProduct = Product.builder()
                .id(productId)
                .name("Test Product")
                .price(new BigDecimal("100.00"))
                .cost(new BigDecimal("50.00"))
                .category(testCategory)
                .business(testBusiness)
                .build();
    }
    
    // ==================== CREATE SALE TESTS ====================
    // TODO: Actualizar tests para la nueva API sin CreateSaleRequest con items
    
    /*@Test
    void createSale_shouldCreateSaleWithItems_whenValidRequest() {
        // Given
        CreateSaleItemRequest itemRequest = new CreateSaleItemRequest(productId, 2);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemRequest));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));
        
        Sale savedSale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("200.00"))
                .build();
        
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        when(saleItemRepository.save(any(SaleItem.class))).thenReturn(new SaleItem());
        
        // When
        UUID saleId = saleService.createSale(userEmail, businessId, request);
        
        // Then
        assertThat(saleId).isNotNull().isEqualTo(savedSale.getId());
        
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository, times(2)).save(saleCaptor.capture());
        
        List<Sale> capturedSales = saleCaptor.getAllValues();
        assertThat(capturedSales.get(0).getBusiness()).isEqualTo(testBusiness);
        assertThat(capturedSales.get(0).getCreatedByUser()).isEqualTo(testUser);
        assertThat(capturedSales.get(1).getTotalAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        
        ArgumentCaptor<SaleItem> itemCaptor = ArgumentCaptor.forClass(SaleItem.class);
        verify(saleItemRepository).save(itemCaptor.capture());
        
        SaleItem capturedItem = itemCaptor.getValue();
        assertThat(capturedItem.getProduct()).isEqualTo(testProduct);
        assertThat(capturedItem.getQuantity()).isEqualTo(2);
        assertThat(capturedItem.getUnitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(capturedItem.getLineTotal()).isEqualByComparingTo(new BigDecimal("200.00"));
    }*/
    
    /*@Test
    void createSale_shouldCalculateTotalCorrectly_whenMultipleItems() {
        // Given
        UUID product2Id = UUID.randomUUID();
        Product product2 = Product.builder()
                .id(product2Id)
                .name("Product 2")
                .price(new BigDecimal("75.50"))
                .cost(new BigDecimal("40.00"))
                .category(testCategory)
                .business(testBusiness)
                .build();
        
        CreateSaleItemRequest item1 = new CreateSaleItemRequest(productId, 3);
        CreateSaleItemRequest item2 = new CreateSaleItemRequest(product2Id, 2);
        CreateSaleRequest request = new CreateSaleRequest(List.of(item1, item2));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));
        when(productRepository.findByIdAndBusinessId(product2Id, businessId))
                .thenReturn(Optional.of(product2));
        
        Sale savedSale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .build();
        
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        when(saleItemRepository.save(any(SaleItem.class))).thenReturn(new SaleItem());
        
        // When
        saleService.createSale(userEmail, businessId, request);
        
        // Then
        ArgumentCaptor<Sale> saleCaptor = ArgumentCaptor.forClass(Sale.class);
        verify(saleRepository, times(2)).save(saleCaptor.capture());
        
        // Total: (100 * 3) + (75.50 * 2) = 300 + 151 = 451
        Sale finalSale = saleCaptor.getAllValues().get(1);
        assertThat(finalSale.getTotalAmount()).isEqualByComparingTo(new BigDecimal("451.00"));
    }*/
    
    /*@Test
    void createSale_shouldCaptureProductDetailsAtSale_whenCreatingItems() {
        // Given
        CreateSaleItemRequest itemRequest = new CreateSaleItemRequest(productId, 1);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemRequest));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));
        
        Sale savedSale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .build();
        
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        when(saleItemRepository.save(any(SaleItem.class))).thenReturn(new SaleItem());
        
        // When
        saleService.createSale(userEmail, businessId, request);
        
        // Then
        ArgumentCaptor<SaleItem> itemCaptor = ArgumentCaptor.forClass(SaleItem.class);
        verify(saleItemRepository).save(itemCaptor.capture());
        
        SaleItem item = itemCaptor.getValue();
        assertThat(item.getProductNameAtSale()).isEqualTo("Test Product");
        assertThat(item.getCategoryName()).isEqualTo("Test Category");
        assertThat(item.getUnitPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(item.getUnitCost()).isEqualByComparingTo(new BigDecimal("50.00"));
    }*/
    
    /*@Test
    void createSale_shouldThrowException_whenUserNotFound() {
        // Given
        CreateSaleRequest request = new CreateSaleRequest(List.of());
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> saleService.createSale(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
        
        verify(saleRepository, never()).save(any());
    }*/
    
    /*@Test
    void createSale_shouldThrowException_whenNoBusinessAccess() {
        // Given
        CreateSaleRequest request = new CreateSaleRequest(List.of());
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> saleService.createSale(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
        
        verify(saleRepository, never()).save(any());
    }*/
    
    /*@Test
    void createSale_shouldThrowException_whenMembershipNotActive() {
        // Given
        activeMembership.setStatus(MembershipStatus.INACTIVE);
        CreateSaleRequest request = new CreateSaleRequest(List.of());
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        
        // When/Then
        assertThatThrownBy(() -> saleService.createSale(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
        
        verify(saleRepository, never()).save(any());
    }*/
    
    /*@Test
    void createSale_shouldThrowException_whenBusinessNotFound() {
        // Given
        CreateSaleRequest request = new CreateSaleRequest(List.of());
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> saleService.createSale(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Negocio no encontrado");
        
        verify(saleRepository, never()).save(any());
    }*/
    
    /*@Test
    void createSale_shouldThrowException_whenProductNotFound() {
        // Given
        CreateSaleItemRequest itemRequest = new CreateSaleItemRequest(productId, 2);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemRequest));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.empty());
        
        Sale savedSale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .build();
        
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        
        // When/Then
        assertThatThrownBy(() -> saleService.createSale(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Producto no encontrado en este negocio");
    }*/
    
    /*@Test
    void createSale_shouldHandleCaseInsensitiveEmail() {
        // Given
        String upperEmail = "TEST@EXAMPLE.COM";
        CreateSaleItemRequest itemRequest = new CreateSaleItemRequest(productId, 1);
        CreateSaleRequest request = new CreateSaleRequest(List.of(itemRequest));
        
        when(userRepository.findByEmailIgnoreCase(upperEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));
        
        Sale savedSale = Sale.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("100.00"))
                .build();
        
        when(saleRepository.save(any(Sale.class))).thenReturn(savedSale);
        when(saleItemRepository.save(any(SaleItem.class))).thenReturn(new SaleItem());
        
        // When
        UUID saleId = saleService.createSale(upperEmail, businessId, request);
        
        // Then
        assertThat(saleId).isNotNull();
        verify(userRepository).findByEmailIgnoreCase(upperEmail);
    }*/
    
    // ==================== GET SALES BY BUSINESS TESTS ====================
    // TODO: Actualizar tests para incluir parámetro Boolean open
    
    /*@Test
    void getSalesByBusiness_shouldReturnAllSales_whenUserIsOwner() {
        // Given
        Sale sale1 = createTestSale(UUID.randomUUID(), new BigDecimal("100.00"));
        Sale sale2 = createTestSale(UUID.randomUUID(), new BigDecimal("200.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of(sale1, sale2));
        when(saleItemRepository.findBySaleId(any())).thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusiness(userEmail, businessId);
        
        // Then
        assertThat(sales).hasSize(2);
        verify(saleRepository).findByBusinessIdOrderByOccurredAtDesc(businessId);
        verify(saleRepository, never()).findByBusinessIdAndCreatedByUserIdOrderByOccurredAtDesc(any(), any());
    }*/
    
    /*@Test
    void getSalesByBusiness_shouldReturnOnlyUserSales_whenUserIsEmployee() {
        // Given
        activeMembership.setRole(MembershipRole.EMPLOYEE);
        
        Sale sale1 = createTestSale(UUID.randomUUID(), new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdAndCreatedByUserIdOrderByOccurredAtDesc(businessId, userId))
                .thenReturn(List.of(sale1));
        when(saleItemRepository.findBySaleId(any())).thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusiness(userEmail, businessId);
        
        // Then
        assertThat(sales).hasSize(1);
        verify(saleRepository).findByBusinessIdAndCreatedByUserIdOrderByOccurredAtDesc(businessId, userId);
        verify(saleRepository, never()).findByBusinessIdOrderByOccurredAtDesc(any());
    }*/
    
    /*@Test
    void getSalesByBusiness_shouldMapToResponseCorrectly() {
        // Given
        UUID saleId = UUID.randomUUID();
        Sale sale = createTestSale(saleId, new BigDecimal("150.00"));
        
        SaleItem item = SaleItem.builder()
                .id(UUID.randomUUID())
                .sale(sale)
                .productNameAtSale("Product A")
                .categoryName("Category A")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .unitCost(new BigDecimal("30.00"))
                .lineTotal(new BigDecimal("100.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of(item));
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusiness(userEmail, businessId);
        
        // Then
        assertThat(sales).hasSize(1);
        SaleResponse response = sales.get(0);
        assertThat(response.id()).isEqualTo(saleId);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.createdByUserName()).isEqualTo("John Doe");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Product A");
    }*/
    
    /*@Test
    void getSalesByBusiness_shouldHandleNullUserGracefully() {
        // Given
        UUID saleId = UUID.randomUUID();
        Sale sale = Sale.builder()
                .id(saleId)
                .business(testBusiness)
                .createdByUser(null)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("100.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId))
                .thenReturn(List.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusiness(userEmail, businessId);
        
        // Then
        assertThat(sales).hasSize(1);
        assertThat(sales.get(0).createdByUserName()).isEqualTo("Sistema");
    }*/
    
    /*@Test
    void getSalesByBusiness_shouldThrowException_whenNoAccess() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> saleService.getSalesByBusiness(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }*/
    
    // ==================== GET SALES BY DATE RANGE TESTS ====================
    
    @Test
    void getSalesByBusinessAndDateRange_shouldReturnAllSales_whenUserIsOwner() {
        // Given
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
        OffsetDateTime endDate = OffsetDateTime.now();
        
        Sale sale1 = createTestSale(UUID.randomUUID(), new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(businessId, startDate, endDate))
                .thenReturn(List.of(sale1));
        when(saleItemRepository.findBySaleId(any())).thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusinessAndDateRange(userEmail, businessId, startDate, endDate);
        
        // Then
        assertThat(sales).hasSize(1);
        verify(saleRepository).findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(businessId, startDate, endDate);
    }
    
    @Test
    void getSalesByBusinessAndDateRange_shouldReturnOnlyUserSales_whenUserIsEmployee() {
        // Given
        activeMembership.setRole(MembershipRole.EMPLOYEE);
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
        OffsetDateTime endDate = OffsetDateTime.now();
        
        Sale sale1 = createTestSale(UUID.randomUUID(), new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                businessId, userId, startDate, endDate))
                .thenReturn(List.of(sale1));
        when(saleItemRepository.findBySaleId(any())).thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusinessAndDateRange(userEmail, businessId, startDate, endDate);
        
        // Then
        assertThat(sales).hasSize(1);
        verify(saleRepository).findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                businessId, userId, startDate, endDate);
    }
    
    @Test
    void getSalesByBusinessAndDateRange_shouldReturnEmptyList_whenNoSalesInRange() {
        // Given
        OffsetDateTime startDate = OffsetDateTime.now().minusDays(7);
        OffsetDateTime endDate = OffsetDateTime.now();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(businessId, startDate, endDate))
                .thenReturn(List.of());
        
        // When
        List<SaleResponse> sales = saleService.getSalesByBusinessAndDateRange(userEmail, businessId, startDate, endDate);
        
        // Then
        assertThat(sales).isEmpty();
    }
    
    // ==================== GET SALE BY ID TESTS ====================
    
    @Test
    void getSaleById_shouldReturnSale_whenOwnerRequests() {
        // Given
        UUID saleId = UUID.randomUUID();
        Sale sale = createTestSale(saleId, new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of());
        
        // When
        SaleResponse response = saleService.getSaleById(userEmail, businessId, saleId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(saleId);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }
    
    @Test
    void getSaleById_shouldReturnSale_whenEmployeeRequestsOwnSale() {
        // Given
        activeMembership.setRole(MembershipRole.EMPLOYEE);
        UUID saleId = UUID.randomUUID();
        Sale sale = createTestSale(saleId, new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of());
        
        // When
        SaleResponse response = saleService.getSaleById(userEmail, businessId, saleId);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(saleId);
    }
    
    @Test
    void getSaleById_shouldThrowException_whenEmployeeRequestsOthersSale() {
        // Given
        activeMembership.setRole(MembershipRole.EMPLOYEE);
        UUID saleId = UUID.randomUUID();
        
        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .name("Jane")
                .lastName("Smith")
                .build();
        
        Sale sale = Sale.builder()
                .id(saleId)
                .business(testBusiness)
                .createdByUser(otherUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("100.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        
        // When/Then
        assertThatThrownBy(() -> saleService.getSaleById(userEmail, businessId, saleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes permisos para ver esta venta");
    }
    
    @Test
    void getSaleById_shouldThrowException_whenSaleNotFound() {
        // Given
        UUID saleId = UUID.randomUUID();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.empty());
        
        // When/Then
        assertThatThrownBy(() -> saleService.getSaleById(userEmail, businessId, saleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Venta no encontrada");
    }
    
    @Test
    void getSaleById_shouldThrowException_whenSaleNotInBusiness() {
        // Given
        UUID saleId = UUID.randomUUID();
        UUID otherBusinessId = UUID.randomUUID();
        
        Business otherBusiness = Business.builder()
                .id(otherBusinessId)
                .name("Other Business")
                .build();
        
        Sale sale = Sale.builder()
                .id(saleId)
                .business(otherBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("100.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        
        // When/Then
        assertThatThrownBy(() -> saleService.getSaleById(userEmail, businessId, saleId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La venta no pertenece a este negocio");
    }
    
    @Test
    void getSaleById_shouldIncludeSaleItems_whenReturningResponse() {
        // Given
        UUID saleId = UUID.randomUUID();
        Sale sale = createTestSale(saleId, new BigDecimal("250.00"));
        
        SaleItem item1 = SaleItem.builder()
                .id(UUID.randomUUID())
                .sale(sale)
                .productNameAtSale("Product A")
                .categoryName("Category A")
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .unitCost(new BigDecimal("30.00"))
                .lineTotal(new BigDecimal("100.00"))
                .build();
        
        SaleItem item2 = SaleItem.builder()
                .id(UUID.randomUUID())
                .sale(sale)
                .productNameAtSale("Product B")
                .categoryName("Category B")
                .quantity(3)
                .unitPrice(new BigDecimal("50.00"))
                .unitCost(new BigDecimal("25.00"))
                .lineTotal(new BigDecimal("150.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of(item1, item2));
        
        // When
        SaleResponse response = saleService.getSaleById(userEmail, businessId, saleId);
        
        // Then
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).productName()).isEqualTo("Product A");
        assertThat(response.items().get(0).quantity()).isEqualTo(2);
        assertThat(response.items().get(1).productName()).isEqualTo("Product B");
        assertThat(response.items().get(1).quantity()).isEqualTo(3);
    }
    
    @Test
    void getSaleById_shouldFormatUserName_whenBothNamesPresent() {
        // Given
        UUID saleId = UUID.randomUUID();
        Sale sale = createTestSale(saleId, new BigDecimal("100.00"));
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of());
        
        // When
        SaleResponse response = saleService.getSaleById(userEmail, businessId, saleId);
        
        // Then
        assertThat(response.createdByUserName()).isEqualTo("John Doe");
    }
    
    @Test
    void getSaleById_shouldHandleSingleName() {
        // Given
        UUID saleId = UUID.randomUUID();
        
        User userWithOnlyName = User.builder()
                .id(userId)
                .email(userEmail)
                .name("John")
                .lastName("")
                .build();
        
        Sale sale = Sale.builder()
                .id(saleId)
                .business(testBusiness)
                .createdByUser(userWithOnlyName)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(new BigDecimal("100.00"))
                .build();
        
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
        when(saleItemRepository.findBySaleId(saleId)).thenReturn(List.of());
        
        // When
        SaleResponse response = saleService.getSaleById(userEmail, businessId, saleId);
        
        // Then
        assertThat(response.createdByUserName()).isEqualTo("John");
    }
    
    // ==================== HELPER METHODS ====================
    
    private Sale createTestSale(UUID saleId, BigDecimal totalAmount) {
        return Sale.builder()
                .id(saleId)
                .business(testBusiness)
                .createdByUser(testUser)
                .occurredAt(OffsetDateTime.now())
                .totalAmount(totalAmount)
                .build();
    }
}
