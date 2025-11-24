package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.BusinessRankingResponse;
import com.nempeth.korven.rest.dto.EmployeeRankingResponse;
import com.nempeth.korven.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for rankings
 * Provides endpoints to retrieve employee and business performance rankings
 */
@RestController
@RequestMapping("/businesses/{businessId}/rankings")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /**
     * Get employee rankings for a business based on current month sales
     * Returns a list of employees ranked by their sales performance during the current month
     * 
     * @param businessId The business ID
     * @param auth The authenticated user
     * @return List of employee rankings with sales count, revenue, and position (current month only)
     */
    @GetMapping("/employees")
    public ResponseEntity<List<EmployeeRankingResponse>> getEmployeeRankings(
            @PathVariable UUID businessId,
            Authentication auth) {
        String userEmail = auth.getName();
        List<EmployeeRankingResponse> rankings = rankingService.getEmployeeRankings(userEmail, businessId);
        return ResponseEntity.ok(rankings);
    }

    /**
     * Get business rankings based on composite performance score
     * Returns a list of all businesses ranked by their composite score which considers:
     * - Total Revenue (30%)
     * - Average Ticket (25%)
     * - Consistency (20%)
     * - Transaction Volume (15%)
     * - Growth (10%)
     * 
     * @param businessId The business ID (used for path consistency, but rankings show all businesses)
     * @param auth The authenticated user
     * @return List of business rankings sorted by composite score
     */
    @GetMapping("/businesses")
    public ResponseEntity<List<BusinessRankingResponse>> getBusinessRankings(
            @PathVariable UUID businessId,
            Authentication auth) {
        String userEmail = auth.getName();
        List<BusinessRankingResponse> rankings = rankingService.getBusinessRankings(userEmail);
        return ResponseEntity.ok(rankings);
    }
}
