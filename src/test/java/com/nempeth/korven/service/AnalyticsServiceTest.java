package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.MonthlyCategoryProfitResponse;
import com.nempeth.korven.rest.dto.MonthlyCategoryRevenueResponse;
import com.nempeth.korven.rest.dto.MonthlyProfitResponse;
import com.nempeth.korven.rest.dto.MonthlyRevenueResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Tests")
class AnalyticsServiceTest {

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    @Captor
    private ArgumentCaptor<OffsetDateTime> dateCaptor;

    private User testUser;
    private Business testBusiness;
    private BusinessMembership activeMembership;
    private UUID businessId;
    private String userEmail;
    private Integer testYear;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        userEmail = "test@example.com";
        testYear = 2025;

        testUser = User.builder()
                .id(UUID.randomUUID())
                .email(userEmail)
                .name("Test")
                .lastName("User")
                .build();

        testBusiness = Business.builder()
                .id(businessId)
                .name("Test Business")
                .joinCode("TEST123")
                .build();

        activeMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser)
                .role(MembershipRole.OWNER)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should return monthly revenue by category for valid user and business")
    void shouldReturnMonthlyRevenueByCategoryForValidUser() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, "Electronics", new BigDecimal("15000.00")},
                new Object[]{2025, 1, "Clothing", new BigDecimal("8000.00")},
                new Object[]{2025, 2, "Electronics", new BigDecimal("18000.00")}
        );

        when(saleRepository.findMonthlyRevenueByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyCategoryRevenueResponse> results = analyticsService.getMonthlyRevenueByCategory(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        
        assertThat(results.get(0).month()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(results.get(0).categoryName()).isEqualTo("Electronics");
        assertThat(results.get(0).revenue()).isEqualByComparingTo(new BigDecimal("15000.00"));

        assertThat(results.get(1).month()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(results.get(1).categoryName()).isEqualTo("Clothing");
        assertThat(results.get(1).revenue()).isEqualByComparingTo(new BigDecimal("8000.00"));

        assertThat(results.get(2).month()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(results.get(2).categoryName()).isEqualTo("Electronics");
        assertThat(results.get(2).revenue()).isEqualByComparingTo(new BigDecimal("18000.00"));

        verify(saleRepository).findMonthlyRevenueByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Should return empty list when no revenue data exists")
    void shouldReturnEmptyListWhenNoRevenueData() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findMonthlyRevenueByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        // When
        List<MonthlyCategoryRevenueResponse> results = analyticsService.getMonthlyRevenueByCategory(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should return monthly profit by category for valid user and business")
    void shouldReturnMonthlyProfitByCategoryForValidUser() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, "Electronics", new BigDecimal("5000.00")},
                new Object[]{2025, 2, "Electronics", new BigDecimal("6000.00")},
                new Object[]{2025, 3, "Clothing", new BigDecimal("2500.00")}
        );

        when(saleRepository.findMonthlyProfitByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyCategoryProfitResponse> results = analyticsService.getMonthlyProfitByCategory(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        
        assertThat(results.get(0).month()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(results.get(0).categoryName()).isEqualTo("Electronics");
        assertThat(results.get(0).profit()).isEqualByComparingTo(new BigDecimal("5000.00"));

        assertThat(results.get(1).month()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(results.get(1).categoryName()).isEqualTo("Electronics");
        assertThat(results.get(1).profit()).isEqualByComparingTo(new BigDecimal("6000.00"));

        verify(saleRepository).findMonthlyProfitByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Should return monthly total revenue for valid user and business")
    void shouldReturnMonthlyTotalRevenueForValidUser() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, new BigDecimal("23000.00")},
                new Object[]{2025, 2, new BigDecimal("28000.00")},
                new Object[]{2025, 3, new BigDecimal("25000.00")}
        );

        when(saleRepository.findMonthlyTotalRevenue(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyRevenueResponse> results = analyticsService.getMonthlyTotalRevenue(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        
        assertThat(results.get(0).month()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(results.get(0).revenue()).isEqualByComparingTo(new BigDecimal("23000.00"));

        assertThat(results.get(1).month()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(results.get(1).revenue()).isEqualByComparingTo(new BigDecimal("28000.00"));

        assertThat(results.get(2).month()).isEqualTo(YearMonth.of(2025, 3));
        assertThat(results.get(2).revenue()).isEqualByComparingTo(new BigDecimal("25000.00"));

        verify(saleRepository).findMonthlyTotalRevenue(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Should return monthly total profit for valid user and business")
    void shouldReturnMonthlyTotalProfitForValidUser() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, new BigDecimal("8000.00")},
                new Object[]{2025, 2, new BigDecimal("9500.00")},
                new Object[]{2025, 3, new BigDecimal("7200.00")}
        );

        when(saleRepository.findMonthlyTotalProfit(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyProfitResponse> results = analyticsService.getMonthlyTotalProfit(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        
        assertThat(results.get(0).month()).isEqualTo(YearMonth.of(2025, 1));
        assertThat(results.get(0).profit()).isEqualByComparingTo(new BigDecimal("8000.00"));

        assertThat(results.get(1).month()).isEqualTo(YearMonth.of(2025, 2));
        assertThat(results.get(1).profit()).isEqualByComparingTo(new BigDecimal("9500.00"));

        assertThat(results.get(2).month()).isEqualTo(YearMonth.of(2025, 3));
        assertThat(results.get(2).profit()).isEqualByComparingTo(new BigDecimal("7200.00"));

        verify(saleRepository).findMonthlyTotalProfit(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analyticsService.getMonthlyRevenueByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");

        assertThatThrownBy(() -> analyticsService.getMonthlyProfitByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalRevenue(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalProfit(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should throw exception when user has no access to business")
    void shouldThrowExceptionWhenUserHasNoAccessToBusiness() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> analyticsService.getMonthlyRevenueByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");

        assertThatThrownBy(() -> analyticsService.getMonthlyProfitByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalRevenue(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalProfit(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes acceso a este negocio");
    }

    @Test
    @DisplayName("Should throw exception when membership is not active")
    void shouldThrowExceptionWhenMembershipIsNotActive() {
        // Given
        BusinessMembership inactiveMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.INACTIVE)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(inactiveMembership));

        // When & Then
        assertThatThrownBy(() -> analyticsService.getMonthlyRevenueByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");

        assertThatThrownBy(() -> analyticsService.getMonthlyProfitByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalRevenue(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");

        assertThatThrownBy(() -> analyticsService.getMonthlyTotalProfit(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
    }

    @Test
    @DisplayName("Should throw exception when membership is pending")
    void shouldThrowExceptionWhenMembershipIsPending() {
        // Given
        BusinessMembership pendingMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser)
                .role(MembershipRole.EMPLOYEE)
                .status(MembershipStatus.PENDING)
                .build();

        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(pendingMembership));

        // When & Then
        assertThatThrownBy(() -> analyticsService.getMonthlyRevenueByCategory(userEmail, businessId, testYear))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu membresía en este negocio no está activa");
    }

    @Test
    @DisplayName("Should use correct date range for the specified year")
    void shouldUseCorrectDateRangeForYear() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findMonthlyTotalRevenue(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        // When
        analyticsService.getMonthlyTotalRevenue(userEmail, businessId, 2024);

        // Then
        verify(saleRepository).findMonthlyTotalRevenue(eq(businessId), dateCaptor.capture(), dateCaptor.capture());
        
        List<OffsetDateTime> capturedDates = dateCaptor.getAllValues();
        OffsetDateTime startDate = capturedDates.get(0);
        OffsetDateTime endDate = capturedDates.get(1);

        // Verify start of year
        assertThat(startDate.getYear()).isEqualTo(2024);
        assertThat(startDate.getMonthValue()).isEqualTo(1);
        assertThat(startDate.getDayOfMonth()).isEqualTo(1);
        assertThat(startDate.getHour()).isEqualTo(0);
        assertThat(startDate.getMinute()).isEqualTo(0);
        assertThat(startDate.getSecond()).isEqualTo(0);

        // Verify end of year
        assertThat(endDate.getYear()).isEqualTo(2024);
        assertThat(endDate.getMonthValue()).isEqualTo(12);
        assertThat(endDate.getDayOfMonth()).isEqualTo(31);
        assertThat(endDate.getHour()).isEqualTo(23);
        assertThat(endDate.getMinute()).isEqualTo(59);
        assertThat(endDate.getSecond()).isEqualTo(59);
    }

    @Test
    @DisplayName("Should handle multiple categories in same month")
    void shouldHandleMultipleCategoriesInSameMonth() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 3, "Electronics", new BigDecimal("10000.00")},
                new Object[]{2025, 3, "Clothing", new BigDecimal("5000.00")},
                new Object[]{2025, 3, "Food", new BigDecimal("3000.00")},
                new Object[]{2025, 3, "Books", new BigDecimal("2000.00")}
        );

        when(saleRepository.findMonthlyRevenueByCategory(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyCategoryRevenueResponse> results = analyticsService.getMonthlyRevenueByCategory(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(4);
        assertThat(results).allMatch(r -> r.month().equals(YearMonth.of(2025, 3)));
        assertThat(results).extracting(MonthlyCategoryRevenueResponse::categoryName)
                .containsExactly("Electronics", "Clothing", "Food", "Books");
    }

    @Test
    @DisplayName("Should handle negative profit values")
    void shouldHandleNegativeProfitValues() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, new BigDecimal("5000.00")},
                new Object[]{2025, 2, new BigDecimal("-1500.00")},  // Negative profit
                new Object[]{2025, 3, new BigDecimal("3000.00")}
        );

        when(saleRepository.findMonthlyTotalProfit(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyProfitResponse> results = analyticsService.getMonthlyTotalProfit(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(1).profit()).isEqualByComparingTo(new BigDecimal("-1500.00"));
    }

    @Test
    @DisplayName("Should handle zero revenue values")
    void shouldHandleZeroRevenueValues() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        List<Object[]> mockResults = List.of(
                new Object[]{2025, 1, new BigDecimal("0.00")},
                new Object[]{2025, 2, new BigDecimal("5000.00")},
                new Object[]{2025, 3, new BigDecimal("0.00")}
        );

        when(saleRepository.findMonthlyTotalRevenue(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(mockResults);

        // When
        List<MonthlyRevenueResponse> results = analyticsService.getMonthlyTotalRevenue(
                userEmail, businessId, testYear);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).revenue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(results.get(2).revenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should work with different year values")
    void shouldWorkWithDifferentYearValues() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(saleRepository.findMonthlyTotalRevenue(eq(businessId), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        // When
        analyticsService.getMonthlyTotalRevenue(userEmail, businessId, 2023);

        // Then
        verify(saleRepository).findMonthlyTotalRevenue(eq(businessId), dateCaptor.capture(), dateCaptor.capture());
        
        List<OffsetDateTime> capturedDates = dateCaptor.getAllValues();
        assertThat(capturedDates.get(0).getYear()).isEqualTo(2023);
        assertThat(capturedDates.get(1).getYear()).isEqualTo(2023);
    }
}
