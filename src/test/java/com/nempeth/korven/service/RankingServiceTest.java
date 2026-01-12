package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.Sale;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.BusinessRankingResponse;
import com.nempeth.korven.rest.dto.EmployeeRankingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService Tests")
class RankingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessMembershipRepository businessMembershipRepository;

    @Mock
    private SaleRepository saleRepository;

    @InjectMocks
    private RankingService rankingService;

    private User testUser1;
    private User testUser2;
    private User testUser3;
    private Business testBusiness;
    private BusinessMembership membership1;
    private BusinessMembership membership2;
    private BusinessMembership membership3;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();

        testUser1 = User.builder()
                .id(UUID.randomUUID())
                .email("user1@example.com")
                .name("María")
                .lastName("González")
                .build();

        testUser2 = User.builder()
                .id(UUID.randomUUID())
                .email("user2@example.com")
                .name("Carlos")
                .lastName("Rodríguez")
                .build();

        testUser3 = User.builder()
                .id(UUID.randomUUID())
                .email("user3@example.com")
                .name("Ana")
                .lastName("Martínez")
                .build();

        testBusiness = Business.builder()
                .id(businessId)
                .name("Test Business")
                .joinCode("TESTCODE123")
                .build();

        membership1 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser1)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        membership2 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser2)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        membership3 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser3)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should return employee rankings sorted by revenue for current month")
    void shouldReturnEmployeeRankingsSortedByRevenue() {
        // Given
        String userEmail = "user1@example.com";

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(testBusiness));
        when(businessMembershipRepository.existsByBusinessIdAndUserId(businessId, testUser1.getId()))
                .thenReturn(true);
        when(businessMembershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(membership1, membership2, membership3));

        // Create mock sales for current month
        List<Sale> salesUser1 = createMockSales(5, new BigDecimal("156000"));
        List<Sale> salesUser2 = createMockSales(4, new BigDecimal("142000"));
        List<Sale> salesUser3 = createMockSales(3, new BigDecimal("128000"));

        // Mock the repository method with any date range (current month)
        when(saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessId), eq(testUser1.getId()), any(), any())).thenReturn(salesUser1);
        when(saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessId), eq(testUser2.getId()), any(), any())).thenReturn(salesUser2);
        when(saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessId), eq(testUser3.getId()), any(), any())).thenReturn(salesUser3);

        // When
        List<EmployeeRankingResponse> rankings = rankingService.getEmployeeRankings(userEmail, businessId);

        // Then
        assertThat(rankings).hasSize(3);
        
        // Verify first place (María)
        assertThat(rankings.get(0).name()).isEqualTo("María");
        assertThat(rankings.get(0).lastName()).isEqualTo("González");
        assertThat(rankings.get(0).salesCount()).isEqualTo(5L);
        assertThat(rankings.get(0).revenue()).isEqualByComparingTo(new BigDecimal("156000"));
        assertThat(rankings.get(0).position()).isEqualTo(1);
        assertThat(rankings.get(0).currentUser()).isTrue();
        
        // Verify second place (Carlos)
        assertThat(rankings.get(1).name()).isEqualTo("Carlos");
        assertThat(rankings.get(1).position()).isEqualTo(2);
        assertThat(rankings.get(1).currentUser()).isFalse();
        
        // Verify third place (Ana)
        assertThat(rankings.get(2).name()).isEqualTo("Ana");
        assertThat(rankings.get(2).position()).isEqualTo(3);
        assertThat(rankings.get(2).currentUser()).isFalse();
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        String userEmail = "nonexistent@example.com";
        
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> rankingService.getEmployeeRankings(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when business not found")
    void shouldThrowExceptionWhenBusinessNotFound() {
        // Given
        String userEmail = "user1@example.com";
        UUID nonExistentBusinessId = UUID.randomUUID();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessRepository.findById(nonExistentBusinessId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> rankingService.getEmployeeRankings(userEmail, nonExistentBusinessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Negocio no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when user has no access to business")
    void shouldThrowExceptionWhenUserHasNoAccess() {
        // Given
        String userEmail = "user1@example.com";

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(testBusiness));
        when(businessMembershipRepository.existsByBusinessIdAndUserId(businessId, testUser1.getId()))
                .thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> rankingService.getEmployeeRankings(userEmail, businessId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("No tienes acceso a este negocio");
    }

    @Test
    @DisplayName("Should handle employees with zero sales in current month")
    void shouldHandleEmployeesWithZeroSales() {
        // Given
        String userEmail = "user1@example.com";

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessRepository.findById(businessId))
                .thenReturn(Optional.of(testBusiness));
        when(businessMembershipRepository.existsByBusinessIdAndUserId(businessId, testUser1.getId()))
                .thenReturn(true);
        when(businessMembershipRepository.findByBusinessId(businessId))
                .thenReturn(List.of(membership1));

        when(saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessId), eq(testUser1.getId()), any(), any())).thenReturn(List.of());

        // When
        List<EmployeeRankingResponse> rankings = rankingService.getEmployeeRankings(userEmail, businessId);

        // Then
        assertThat(rankings).hasSize(1);
        assertThat(rankings.get(0).salesCount()).isEqualTo(0L);
        assertThat(rankings.get(0).revenue()).isEqualTo(BigDecimal.ZERO);
        assertThat(rankings.get(0).position()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return business rankings sorted by composite score")
    void shouldReturnBusinessRankingsSortedByCompositeScore() {
        // Given
        String userEmail = "user1@example.com";
        
        Business business1 = Business.builder()
                .id(UUID.randomUUID())
                .name("Top Business")
                .joinCode("CODE1")
                .build();
        
        Business business2 = Business.builder()
                .id(UUID.randomUUID())
                .name("Mid Business")
                .joinCode("CODE2")
                .build();
        
        Business business3 = Business.builder()
                .id(UUID.randomUUID())
                .name("Low Business")
                .joinCode("CODE3")
                .build();

        BusinessMembership userMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(business1)
                .user(testUser1)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessMembershipRepository.findByUserId(testUser1.getId()))
                .thenReturn(List.of(userMembership));
        when(businessRepository.findAll())
                .thenReturn(List.of(business1, business2, business3));

        // Mock sales for current and previous month
        List<Sale> currentSalesB1 = createMockSalesWithTimestamp(10, new BigDecimal("500000"), OffsetDateTime.now());
        List<Sale> previousSalesB1 = createMockSalesWithTimestamp(8, new BigDecimal("400000"), OffsetDateTime.now().minusMonths(1));
        
        List<Sale> currentSalesB2 = createMockSalesWithTimestamp(7, new BigDecimal("350000"), OffsetDateTime.now());
        List<Sale> previousSalesB2 = createMockSalesWithTimestamp(6, new BigDecimal("300000"), OffsetDateTime.now().minusMonths(1));
        
        List<Sale> currentSalesB3 = createMockSalesWithTimestamp(3, new BigDecimal("150000"), OffsetDateTime.now());
        List<Sale> previousSalesB3 = createMockSalesWithTimestamp(2, new BigDecimal("100000"), OffsetDateTime.now().minusMonths(1));

        // Mock current month sales
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(business1.getId()), any(), any())).thenReturn(currentSalesB1, previousSalesB1);
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(business2.getId()), any(), any())).thenReturn(currentSalesB2, previousSalesB2);
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(business3.getId()), any(), any())).thenReturn(currentSalesB3, previousSalesB3);

        // When
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);

        // Then
        assertThat(rankings).hasSize(3);
        
        // Verify rankings are sorted by composite score (descending)
        assertThat(rankings.get(0).businessName()).isEqualTo("Top Business");
        assertThat(rankings.get(0).position()).isEqualTo(1);
        assertThat(rankings.get(0).isOwnBusiness()).isTrue();
        assertThat(rankings.get(0).compositeScore()).isGreaterThan(BigDecimal.ZERO);
        
        assertThat(rankings.get(1).businessName()).isEqualTo("Mid Business");
        assertThat(rankings.get(1).position()).isEqualTo(2);
        assertThat(rankings.get(1).isOwnBusiness()).isFalse();
        
        assertThat(rankings.get(2).businessName()).isEqualTo("Low Business");
        assertThat(rankings.get(2).position()).isEqualTo(3);
        assertThat(rankings.get(2).isOwnBusiness()).isFalse();
        
        // Verify scores are in descending order
        assertThat(rankings.get(0).compositeScore())
                .isGreaterThanOrEqualTo(rankings.get(1).compositeScore());
        assertThat(rankings.get(1).compositeScore())
                .isGreaterThanOrEqualTo(rankings.get(2).compositeScore());
    }

    @Test
    @DisplayName("Should return empty list when user has no business memberships")
    void shouldReturnEmptyListWhenUserHasNoMemberships() {
        // Given
        String userEmail = "user1@example.com";

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessMembershipRepository.findByUserId(testUser1.getId()))
                .thenReturn(List.of());

        // When
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);

        // Then
        assertThat(rankings).isEmpty();
        verify(businessRepository, never()).findAll();
    }

    @Test
    @DisplayName("Should throw exception when user not found for business rankings")
    void shouldThrowExceptionWhenUserNotFoundForBusinessRankings() {
        // Given
        String userEmail = "nonexistent@example.com";
        
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> rankingService.getBusinessRankings(userEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should handle businesses with zero sales")
    void shouldHandleBusinessesWithZeroSales() {
        // Given
        String userEmail = "user1@example.com";
        
        Business businessWithSales = Business.builder()
                .id(UUID.randomUUID())
                .name("Active Business")
                .joinCode("ACTIVE")
                .build();
        
        Business businessWithoutSales = Business.builder()
                .id(UUID.randomUUID())
                .name("Inactive Business")
                .joinCode("INACTIVE")
                .build();

        BusinessMembership membership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(businessWithSales)
                .user(testUser1)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessMembershipRepository.findByUserId(testUser1.getId()))
                .thenReturn(List.of(membership));
        when(businessRepository.findAll())
                .thenReturn(List.of(businessWithSales, businessWithoutSales));

        List<Sale> sales = createMockSalesWithTimestamp(5, new BigDecimal("250000"), OffsetDateTime.now());
        
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessWithSales.getId()), any(), any())).thenReturn(sales, List.of());
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                eq(businessWithoutSales.getId()), any(), any())).thenReturn(List.of(), List.of());

        // When
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);

        // Then
        assertThat(rankings).hasSize(2);
        
        // Business with sales should be first
        assertThat(rankings.get(0).businessName()).isEqualTo("Active Business");
        assertThat(rankings.get(0).compositeScore()).isGreaterThan(BigDecimal.ZERO);
        
        // Business without sales should be last with zero score
        assertThat(rankings.get(1).businessName()).isEqualTo("Inactive Business");
        assertThat(rankings.get(1).compositeScore()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should correctly identify own vs other businesses")
    void shouldCorrectlyIdentifyOwnBusinesses() {
        // Given
        String userEmail = "user1@example.com";
        
        Business ownBusiness1 = Business.builder()
                .id(UUID.randomUUID())
                .name("Own Business 1")
                .joinCode("OWN1")
                .build();
        
        Business ownBusiness2 = Business.builder()
                .id(UUID.randomUUID())
                .name("Own Business 2")
                .joinCode("OWN2")
                .build();
        
        Business otherBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Other Business")
                .joinCode("OTHER")
                .build();

        BusinessMembership membership1 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(ownBusiness1)
                .user(testUser1)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        BusinessMembership membership2 = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(ownBusiness2)
                .user(testUser1)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessMembershipRepository.findByUserId(testUser1.getId()))
                .thenReturn(List.of(membership1, membership2));
        when(businessRepository.findAll())
                .thenReturn(List.of(ownBusiness1, ownBusiness2, otherBusiness));

        // Mock empty sales for all businesses
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                any(), any(), any())).thenReturn(List.of());

        // When
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);

        // Then
        assertThat(rankings).hasSize(3);
        
        long ownBusinessCount = rankings.stream()
                .filter(BusinessRankingResponse::isOwnBusiness)
                .count();
        
        assertThat(ownBusinessCount).isEqualTo(2);
        
        // Verify specific businesses
        assertThat(rankings.stream()
                .filter(r -> r.businessName().equals("Own Business 1"))
                .findFirst()
                .orElseThrow()
                .isOwnBusiness()).isTrue();
        
        assertThat(rankings.stream()
                .filter(r -> r.businessName().equals("Own Business 2"))
                .findFirst()
                .orElseThrow()
                .isOwnBusiness()).isTrue();
        
        assertThat(rankings.stream()
                .filter(r -> r.businessName().equals("Other Business"))
                .findFirst()
                .orElseThrow()
                .isOwnBusiness()).isFalse();
    }

    @Test
    @DisplayName("Should assign sequential positions starting from 1")
    void shouldAssignSequentialPositions() {
        // Given
        String userEmail = "user1@example.com";
        
        List<Business> businesses = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            businesses.add(Business.builder()
                    .id(UUID.randomUUID())
                    .name("Business " + (i + 1))
                    .joinCode("CODE" + i)
                    .build());
        }

        BusinessMembership membership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(businesses.get(0))
                .user(testUser1)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser1));
        when(businessMembershipRepository.findByUserId(testUser1.getId()))
                .thenReturn(List.of(membership));
        when(businessRepository.findAll())
                .thenReturn(businesses);
        when(saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                any(), any(), any())).thenReturn(List.of());

        // When
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);

        // Then
        assertThat(rankings).hasSize(5);
        
        for (int i = 0; i < 5; i++) {
            assertThat(rankings.get(i).position()).isEqualTo(i + 1);
        }
    }

    /**
     * Helper method to create mock sales
     */
    private List<Sale> createMockSales(int count, BigDecimal totalRevenue) {
        List<Sale> sales = new ArrayList<>();
        BigDecimal revenuePerSale = totalRevenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        
        for (int i = 0; i < count; i++) {
            Sale sale = Sale.builder()
                    .id(UUID.randomUUID())
                    .totalAmount(revenuePerSale)
                    .business(testBusiness)
                    .build();
            sales.add(sale);
        }
        
        return sales;
    }

    /**
     * Helper method to create mock sales with timestamp
     */
    private List<Sale> createMockSalesWithTimestamp(int count, BigDecimal totalRevenue, OffsetDateTime timestamp) {
        List<Sale> sales = new ArrayList<>();
        BigDecimal revenuePerSale = totalRevenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        
        for (int i = 0; i < count; i++) {
            Sale sale = Sale.builder()
                    .id(UUID.randomUUID())
                    .totalAmount(revenuePerSale)
                    .occurredAt(timestamp)
                    .build();
            sales.add(sale);
        }
        
        return sales;
    }
}
