package com.nempeth.korven.service;

import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.SaleRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.EmployeeRankingResponse;
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
}
