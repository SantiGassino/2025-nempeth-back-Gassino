package com.nempeth.korven.rest;

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
 * REST Controller for employee rankings
 * Provides endpoints to retrieve employee performance rankings
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
}
