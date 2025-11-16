package com.nempeth.korven.service;

import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.Sale;
import com.nempeth.korven.rest.dto.BusinessRankingResponse;
import com.nempeth.korven.rest.dto.EmployeeRankingResponse;
import com.nempeth.korven.utils.ScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository businessMembershipRepository;
    private final SaleRepository saleRepository;

    /**
     * Get employee rankings for a specific business based on current month sales
     * Employees are ranked by total revenue (sales sum) from the current month
     * 
     * @param userEmail Email of the requesting user
     * @param businessId Business ID
     * @return List of employee rankings sorted by revenue (descending)
     */
    @Transactional(readOnly = true)
    public List<EmployeeRankingResponse> getEmployeeRankings(String userEmail, UUID businessId) {
        // Verify user exists
        User requestingUser = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Verify business exists
        businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        // Verify user has access to this business
        boolean hasAccess = businessMembershipRepository
                .existsByBusinessIdAndUserId(businessId, requestingUser.getId());
        if (!hasAccess) {
            throw new AccessDeniedException("No tienes acceso a este negocio");
        }

        // Calculate current month date range
        YearMonth currentMonth = YearMonth.now();
        OffsetDateTime startOfMonth = currentMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset());

        // Get all business members
        List<BusinessMembership> memberships = businessMembershipRepository
                .findByBusinessId(businessId);

        // Calculate rankings
        List<EmployeeRankingData> rankingData = new ArrayList<>();
        
        for (BusinessMembership membership : memberships) {
            User employee = membership.getUser();
            
            // Get sales made by this employee in the current month
            var salesInMonth = saleRepository
                    .findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                            businessId, 
                            employee.getId(),
                            startOfMonth,
                            endOfMonth
                    );
            
            // Count sales made by this employee in the current month
            long salesCount = salesInMonth.size();
            
            // Calculate total revenue from sales made by this employee in the current month
            BigDecimal totalRevenue = salesInMonth
                    .stream()
                    .map(sale -> sale.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            rankingData.add(new EmployeeRankingData(
                    employee.getId(),
                    employee.getName() != null ? employee.getName() : "",
                    employee.getLastName() != null ? employee.getLastName() : "",
                    salesCount,
                    totalRevenue
            ));
        }

        // Sort by revenue (descending), then by sales count (descending)
        rankingData.sort(Comparator
                .comparing(EmployeeRankingData::totalRevenue, Comparator.reverseOrder())
                .thenComparing(EmployeeRankingData::salesCount, Comparator.reverseOrder())
        );

        // Build response with positions
        List<EmployeeRankingResponse> rankings = new ArrayList<>();
        int position = 1;
        
        for (EmployeeRankingData data : rankingData) {
            boolean isCurrentUser = data.userId().equals(requestingUser.getId());
            
            rankings.add(new EmployeeRankingResponse(
                    data.name(),
                    data.lastName(),
                    data.salesCount(),
                    data.totalRevenue(),
                    position,
                    isCurrentUser
            ));
            
            position++;
        }

        return rankings;
    }

    /**
     * Get business rankings based on composite score
     * Businesses are ranked by their performance score which considers:
     * - Total Revenue (30%)
     * - Average Ticket (25%)
     * - Consistency (20%)
     * - Transaction Volume (15%)
     * - Growth (10%)
     * 
     * @param userEmail Email of the requesting user
     * @return List of business rankings sorted by composite score (descending)
     */
    @Transactional(readOnly = true)
    public List<BusinessRankingResponse> getBusinessRankings(String userEmail) {
        // Verify user exists
        User requestingUser = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Get all businesses the user has access to
        List<BusinessMembership> userMemberships = businessMembershipRepository
                .findByUserId(requestingUser.getId());

        if (userMemberships.isEmpty()) {
            return List.of();
        }

        // Get IDs of businesses user has access to
        List<UUID> accessibleBusinessIds = userMemberships.stream()
                .map(m -> m.getBusiness().getId())
                .collect(Collectors.toList());

        // Get all businesses
        List<Business> allBusinesses = businessRepository.findAll();

        // Calculate current and previous month date ranges
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);

        OffsetDateTime currentStart = currentMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime currentEnd = currentMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset());

        OffsetDateTime previousStart = previousMonth.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime previousEnd = previousMonth.atEndOfMonth().atTime(23, 59, 59).atOffset(OffsetDateTime.now().getOffset());

        // First pass: Calculate metrics for all businesses to find dynamic maximums
        double maxRevenue = 0.0;
        double maxAvgTicket = 0.0;
        double maxTransactionCount = 0.0;

        List<BusinessMetrics> businessMetricsList = new ArrayList<>();

        for (Business business : allBusinesses) {
            List<Sale> currentMonthSales = saleRepository
                    .findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                            business.getId(),
                            currentStart,
                            currentEnd
                    );

            List<Sale> previousMonthSales = saleRepository
                    .findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
                            business.getId(),
                            previousStart,
                            previousEnd
                    );

            // Calculate metrics to find maximums
            BigDecimal totalRevenue = ScoreCalculator.calculateTotalRevenue(currentMonthSales);
            long transactionCount = currentMonthSales.size();
            BigDecimal avgTicket = ScoreCalculator.calculateAverageTicket(totalRevenue, transactionCount);

            // Track maximums across all businesses
            maxRevenue = Math.max(maxRevenue, totalRevenue.doubleValue());
            maxAvgTicket = Math.max(maxAvgTicket, avgTicket.doubleValue());
            maxTransactionCount = Math.max(maxTransactionCount, transactionCount);

            businessMetricsList.add(new BusinessMetrics(
                    business,
                    currentMonthSales,
                    previousMonthSales
            ));
        }

        // Second pass: Calculate composite scores using dynamic maximums
        List<BusinessRankingData> rankingData = new ArrayList<>();

        for (BusinessMetrics metrics : businessMetricsList) {
            BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                    metrics.currentMonthSales(),
                    metrics.previousMonthSales(),
                    maxRevenue,
                    maxAvgTicket,
                    maxTransactionCount
            );

            rankingData.add(new BusinessRankingData(
                    metrics.business().getId(),
                    metrics.business().getName(),
                    compositeScore
            ));
        }

        // Sort by composite score (descending)
        rankingData.sort(Comparator
                .comparing(BusinessRankingData::compositeScore, Comparator.reverseOrder())
        );

        // Build response with positions
        List<BusinessRankingResponse> rankings = new ArrayList<>();
        int position = 1;

        for (BusinessRankingData data : rankingData) {
            boolean isOwnBusiness = accessibleBusinessIds.contains(data.businessId());

            rankings.add(new BusinessRankingResponse(
                    data.businessId(),
                    data.businessName(),
                    data.compositeScore(),
                    position,
                    isOwnBusiness
            ));

            position++;
        }

        return rankings;
    }

    /**
     * Internal record to hold employee ranking data before creating response
     */
    private record EmployeeRankingData(
            UUID userId,
            String name,
            String lastName,
            Long salesCount,
            BigDecimal totalRevenue
    ) {
    }

    /**
     * Internal record to hold business metrics during calculation
     */
    private record BusinessMetrics(
            Business business,
            List<Sale> currentMonthSales,
            List<Sale> previousMonthSales
    ) {
    }

    /**
     * Internal record to hold business ranking data before creating response
     */
    private record BusinessRankingData(
            UUID businessId,
            String businessName,
            BigDecimal compositeScore
    ) {
    }
}
