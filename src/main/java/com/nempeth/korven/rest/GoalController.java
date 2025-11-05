package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.*;
import com.nempeth.korven.service.GoalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/businesses/{businessId}/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getAllGoals(@PathVariable UUID businessId,
                                                          Authentication auth) {
        String userEmail = auth.getName();
        List<GoalResponse> goals = goalService.getAllGoalsByBusiness(userEmail, businessId);
        return ResponseEntity.ok(goals);
    }

    @GetMapping("/{goalId}")
    public ResponseEntity<GoalResponse> getGoalById(@PathVariable UUID businessId,
                                                   @PathVariable UUID goalId,
                                                   Authentication auth) {
        String userEmail = auth.getName();
        GoalResponse goal = goalService.getGoalById(userEmail, businessId, goalId);
        return ResponseEntity.ok(goal);
    }

    @GetMapping("/historical")
    public ResponseEntity<List<GoalReportResponse>> getHistoricalReport(@PathVariable UUID businessId,
                                                                        Authentication auth) {
        String userEmail = auth.getName();
        List<GoalReportResponse> report = goalService.getHistoricalReport(userEmail, businessId);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/summary")
    public ResponseEntity<List<ActiveGoalSummaryResponse>> getGoalsSummary(@PathVariable UUID businessId,
                                                                           Authentication auth) {
        String userEmail = auth.getName();
        List<ActiveGoalSummaryResponse> goalsSummary = goalService.getGoalsSummary(userEmail, businessId);
        return ResponseEntity.ok(goalsSummary);
    }

    @GetMapping("/{goalId}/report")
    public ResponseEntity<GoalReportResponse> getGoalReport(@PathVariable UUID businessId,
                                                           @PathVariable UUID goalId,
                                                           Authentication auth) {
        String userEmail = auth.getName();
        GoalReportResponse report = goalService.getGoalReport(userEmail, businessId, goalId);
        return ResponseEntity.ok(report);
    }

    @PostMapping
    public ResponseEntity<?> createGoal(@PathVariable UUID businessId,
                                       @Valid @RequestBody CreateGoalRequest request,
                                       Authentication auth) {
        String userEmail = auth.getName();
        GoalResponse goal = goalService.createGoal(userEmail, businessId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Meta creada exitosamente",
                "goal", goal
        ));
    }

    @PutMapping("/{goalId}")
    public ResponseEntity<?> updateGoal(@PathVariable UUID businessId,
                                       @PathVariable UUID goalId,
                                       @Valid @RequestBody UpdateGoalRequest request,
                                       Authentication auth) {
        String userEmail = auth.getName();
        GoalResponse goal = goalService.updateGoal(userEmail, businessId, goalId, request);
        
        return ResponseEntity.ok(Map.of(
                "message", "Meta actualizada exitosamente",
                "goal", goal
        ));
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<?> deleteGoal(@PathVariable UUID businessId,
                                       @PathVariable UUID goalId,
                                       Authentication auth) {
        String userEmail = auth.getName();
        goalService.deleteGoal(userEmail, businessId, goalId);
        
        return ResponseEntity.ok(Map.of("message", "Meta eliminada exitosamente"));
    }
}
