package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final BusinessRepository businessRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final SaleRepository saleRepository;

    @Transactional(readOnly = true)
    public List<GoalResponse> getAllGoalsByBusiness(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        List<Goal> goals = goalRepository.findByBusinessIdOrderByPeriodStartDesc(businessId);
        return goals.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse getGoalById(String userEmail, UUID businessId, UUID goalId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        Goal goal = goalRepository.findByIdAndBusinessId(goalId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Meta no encontrada"));
        
        return mapToResponse(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalReportResponse> getHistoricalReport(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        List<Goal> historicalGoals = goalRepository.findHistoricalGoals(businessId, LocalDate.now());
        
        return historicalGoals.stream()
                .map(this::mapToReportResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActiveGoalSummaryResponse> getGoalsSummary(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        List<Goal> goals = goalRepository.findByBusinessIdOrderByPeriodStartDesc(businessId);
        LocalDate today = LocalDate.now();
        
        return goals.stream()
                .map(goal -> mapToActiveGoalSummary(goal, today))
                .toList();
    }

    @Transactional(readOnly = true)
    public GoalReportResponse getGoalReport(String userEmail, UUID businessId, UUID goalId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        Goal goal = goalRepository.findByIdAndBusinessId(goalId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Meta no encontrada"));
        
        return mapToReportResponse(goal);
    }

    @Transactional
    public GoalResponse createGoal(String userEmail, UUID businessId, CreateGoalRequest request) {
        validateUserBusinessAccess(userEmail, businessId);
        validatePeriod(request.periodStart(), request.periodEnd());
        validateNoOverlappingGoals(businessId, request.periodStart(), request.periodEnd(), null);
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));
        
        // Validar que las categorías existan
        Map<UUID, Category> categoryMap = validateAndGetCategories(businessId, request.categoryTargets());
        
        Goal goal = Goal.builder()
                .business(business)
                .name(request.name())
                .periodStart(request.periodStart())
                .periodEnd(request.periodEnd())
                .totalRevenueGoal(request.totalRevenueGoal())
                .isLocked(false)
                .build();
        
        goal = goalRepository.save(goal);
        
        // Crear los objetivos de categoría
        for (CategoryTargetRequest targetRequest : request.categoryTargets()) {
            Category category = categoryMap.get(targetRequest.categoryId());
            
            GoalCategoryTarget target = GoalCategoryTarget.builder()
                    .goal(goal)
                    .category(category)
                    .categoryName(category.getName())
                    .revenueTarget(targetRequest.revenueTarget())
                    .build();
            
            goal.addCategoryTarget(target);
        }
        
        goal = goalRepository.save(goal);
        
        return mapToResponse(goal);
    }

    @Transactional
    public GoalResponse updateGoal(String userEmail, UUID businessId, UUID goalId, UpdateGoalRequest request) {
        validateUserBusinessAccess(userEmail, businessId);
        
        Goal goal = goalRepository.findByIdAndBusinessId(goalId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Meta no encontrada"));

        if (goal.isPeriodFinished()) {
            throw new IllegalStateException("No se puede editar una meta que ya finalizó");
        }
        
        validatePeriod(request.periodStart(), request.periodEnd());
        validateNoOverlappingGoals(businessId, request.periodStart(), request.periodEnd(), goalId);
        
        // Validar que las categorías existan
        Map<UUID, Category> categoryMap = validateAndGetCategories(businessId, request.categoryTargets());
        
        // Actualizar campos básicos
        goal.setName(request.name());
        goal.setPeriodStart(request.periodStart());
        goal.setPeriodEnd(request.periodEnd());
        goal.setTotalRevenueGoal(request.totalRevenueGoal());
        
        // Actualizar objetivos de categoría
        goal.getCategoryTargets().clear();
        goalRepository.saveAndFlush(goal); // Forzar flush para eliminar los registros de la BD
        
        // Ahora agregamos los nuevos targets
        for (CategoryTargetRequest targetRequest : request.categoryTargets()) {
            Category category = categoryMap.get(targetRequest.categoryId());
            
            GoalCategoryTarget target = GoalCategoryTarget.builder()
                    .goal(goal)
                    .category(category)
                    .categoryName(category.getName())
                    .revenueTarget(targetRequest.revenueTarget())
                    .build();
            
            goal.addCategoryTarget(target);
        }
        
        goal = goalRepository.save(goal);
        
        return mapToResponse(goal);
    }

    @Transactional
    public void deleteGoal(String userEmail, UUID businessId, UUID goalId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        Goal goal = goalRepository.findByIdAndBusinessId(goalId, businessId)
                .orElseThrow(() -> new IllegalArgumentException("Meta no encontrada"));
        
        // Se puede eliminar cualquier meta, incluso las terminadas
        goalRepository.delete(goal);
    }

    private void validateUserBusinessAccess(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tiene acceso a este negocio"));
        
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Su membresía no está activa");
        }
    }

    private void validatePeriod(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
    }

    private void validateNoOverlappingGoals(UUID businessId, LocalDate start, LocalDate end, UUID excludeGoalId) {
        List<Goal> overlappingGoals = goalRepository.findOverlappingGoals(businessId, start, end);
        
        if (excludeGoalId != null) {
            overlappingGoals = overlappingGoals.stream()
                    .filter(g -> !g.getId().equals(excludeGoalId))
                    .toList();
        }
        
        if (!overlappingGoals.isEmpty()) {
            throw new IllegalArgumentException("Ya existe una meta en el período especificado. Los períodos no pueden superponerse.");
        }
    }

    private Map<UUID, Category> validateAndGetCategories(UUID businessId, List<CategoryTargetRequest> targets) {
        Set<UUID> categoryIds = targets.stream()
                .map(CategoryTargetRequest::categoryId)
                .collect(Collectors.toSet());
        
        List<Category> categories = categoryRepository.findAllById(categoryIds);
        
        if (categories.size() != categoryIds.size()) {
            throw new IllegalArgumentException("Una o más categorías no existen");
        }
        
        // Validar que todas las categorías pertenezcan al negocio
        boolean allBelongToBusiness = categories.stream()
                .allMatch(c -> c.getBusiness().getId().equals(businessId));
        
        if (!allBelongToBusiness) {
            throw new IllegalArgumentException("Una o más categorías no pertenecen al negocio");
        }
        
        return categories.stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
    }

    private GoalResponse mapToResponse(Goal goal) {
        List<GoalCategoryTargetResponse> targetResponses = goal.getCategoryTargets().stream()
                .map(this::mapTargetToResponse)
                .toList();
        
        return new GoalResponse(
                goal.getId(),
                goal.getBusiness().getId(),
                goal.getName(),
                goal.getPeriodStart(),
                goal.getPeriodEnd(),
                goal.getIsLocked(),
                goal.isPeriodFinished(),
                targetResponses
        );
    }

    private GoalCategoryTargetResponse mapTargetToResponse(GoalCategoryTarget target) {
        // Calcular ingresos reales para la categoría en el período
        BigDecimal actualRevenue = calculateActualRevenue(
                target.getGoal().getBusiness().getId(),
                target.getCategoryName(),
                target.getGoal().getPeriodStart(),
                target.getGoal().getPeriodEnd()
        );
        
        // Calcular porcentaje de logro
        BigDecimal achievement = BigDecimal.ZERO;
        if (target.getRevenueTarget().compareTo(BigDecimal.ZERO) > 0) {
            achievement = actualRevenue
                    .divide(target.getRevenueTarget(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        
        return new GoalCategoryTargetResponse(
                target.getId(),
                target.getCategory().getId(),
                target.getCategoryName(),
                target.getRevenueTarget(),
                actualRevenue,
                achievement
        );
    }

    private GoalReportResponse mapToReportResponse(Goal goal) {
        List<GoalCategoryTargetResponse> targetResponses = goal.getCategoryTargets().stream()
                .map(this::mapTargetToResponse)
                .toList();
        
        // Calcular totales
        BigDecimal totalActualRevenue = targetResponses.stream()
                .map(GoalCategoryTargetResponse::actualRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalAchievement = BigDecimal.ZERO;
        if (goal.getTotalRevenueGoal() != null && goal.getTotalRevenueGoal().compareTo(BigDecimal.ZERO) > 0) {
            totalAchievement = totalActualRevenue
                    .divide(goal.getTotalRevenueGoal(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        
        return new GoalReportResponse(
                goal.getId(),
                goal.getName(),
                goal.getPeriodStart(),
                goal.getPeriodEnd(),
                goal.getTotalRevenueGoal(),
                totalActualRevenue,
                totalAchievement,
                targetResponses
        );
    }

    private BigDecimal calculateActualRevenue(UUID businessId, String categoryName, 
                                             LocalDate startDate, LocalDate endDate) {
        // Calcular los ingresos reales de ventas en el período para la categoría
        return saleRepository.calculateRevenueByCategoryAndDateRange(
                businessId, categoryName, startDate, endDate
        ).orElse(BigDecimal.ZERO);
    }

    private ActiveGoalSummaryResponse mapToActiveGoalSummary(Goal goal, LocalDate today) {
        List<GoalCategoryTargetResponse> targetResponses = goal.getCategoryTargets().stream()
                .map(this::mapTargetToResponse)
                .toList();
        
        // Calcular días restantes o estado
        String daysRemaining;
        if (today.isBefore(goal.getPeriodStart())) {
            daysRemaining = "Sin iniciar";
        } else if (today.isAfter(goal.getPeriodEnd())) {
            daysRemaining = "Vencida";
        } else {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today, goal.getPeriodEnd());
            daysRemaining = days + " días";
        }
        
        // Contar categorías completadas (achievement >= 100%)
        int categoriesCompleted = (int) targetResponses.stream()
                .filter(t -> t.achievement().compareTo(new BigDecimal("100")) >= 0)
                .count();
        
        int categoriesTotal = targetResponses.size();
        
        // Determinar estado de finalización
        String completionStatus;
        if (categoriesCompleted == 0) {
            completionStatus = "Sin iniciar";
        } else if (categoriesCompleted == categoriesTotal) {
            completionStatus = "Completado";
        } else {
            completionStatus = "En progreso";
        }
        
        // Calcular totales
        BigDecimal totalActual = targetResponses.stream()
                .map(GoalCategoryTargetResponse::actualRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new ActiveGoalSummaryResponse(
                goal.getId(),
                goal.getName(),
                goal.getPeriodStart(),
                goal.getPeriodEnd(),
                daysRemaining,
                categoriesCompleted,
                categoriesTotal,
                completionStatus,
                goal.getTotalRevenueGoal(),
                totalActual
        );
    }
}
