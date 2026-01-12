package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SaleRepository saleRepository;

    @InjectMocks
    private GoalService goalService;

    private User testUser;
    private Business testBusiness;
    private BusinessMembership activeMembership;
    private Category category1;
    private Category category2;
    private Goal testGoal;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .name("Test")
                .lastName("User")
                .build();

        testBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode("ABC12345")
                .build();

        activeMembership = BusinessMembership.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .user(testUser)
                .status(MembershipStatus.ACTIVE)
                .build();

        category1 = Category.builder()
                .id(UUID.randomUUID())
                .name("Electrónica")
                .business(testBusiness)
                .build();

        category2 = Category.builder()
                .id(UUID.randomUUID())
                .name("Ropa")
                .business(testBusiness)
                .build();

        testGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Meta Q1 2026")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 3, 31))
                .totalRevenueGoal(new BigDecimal("50000.00"))
                .isLocked(false)
                .build();

        GoalCategoryTarget target1 = GoalCategoryTarget.builder()
                .id(UUID.randomUUID())
                .goal(testGoal)
                .categoryId(category1.getId())
                .categoryName("Electrónica")
                .revenueTarget(new BigDecimal("30000.00"))
                .build();

        GoalCategoryTarget target2 = GoalCategoryTarget.builder()
                .id(UUID.randomUUID())
                .goal(testGoal)
                .categoryId(category2.getId())
                .categoryName("Ropa")
                .revenueTarget(new BigDecimal("20000.00"))
                .build();

        testGoal.addCategoryTarget(target1);
        testGoal.addCategoryTarget(target2);
    }

    // ==================== getAllGoalsByBusiness Tests ====================

    @Test
    void shouldGetAllGoalsByBusiness() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByBusinessIdOrderByPeriodStartDesc(testBusiness.getId()))
                .thenReturn(List.of(testGoal));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("15000.00")));

        List<GoalResponse> result = goalService.getAllGoalsByBusiness("test@example.com", testBusiness.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Meta Q1 2026");
        assertThat(result.get(0).categoryTargets()).hasSize(2);
        verify(goalRepository).findByBusinessIdOrderByPeriodStartDesc(testBusiness.getId());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundInGetAllGoals() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getAllGoalsByBusiness("test@example.com", testBusiness.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado");
    }

    @Test
    void shouldThrowExceptionWhenNoBusinessAccessInGetAllGoals() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getAllGoalsByBusiness("test@example.com", testBusiness.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tiene acceso a este negocio");
    }

    @Test
    void shouldThrowExceptionWhenMembershipInactiveInGetAllGoals() {
        activeMembership.setStatus(MembershipStatus.INACTIVE);
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        assertThatThrownBy(() -> goalService.getAllGoalsByBusiness("test@example.com", testBusiness.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Su membresía no está activa");
    }

    // ==================== getGoalById Tests ====================

    @Test
    void shouldGetGoalById() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("15000.00")));

        GoalResponse result = goalService.getGoalById("test@example.com", testBusiness.getId(), testGoal.getId());

        assertThat(result.id()).isEqualTo(testGoal.getId());
        assertThat(result.name()).isEqualTo("Meta Q1 2026");
        assertThat(result.categoryTargets()).hasSize(2);
        verify(goalRepository).findByIdAndBusinessId(testGoal.getId(), testBusiness.getId());
    }

    @Test
    void shouldThrowExceptionWhenGoalNotFoundInGetById() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.getGoalById("test@example.com", testBusiness.getId(), testGoal.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Meta no encontrada");
    }

    // ==================== getHistoricalReport Tests ====================

    @Test
    void shouldGetHistoricalReport() {
        Goal historicalGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Meta Q4 2025")
                .periodStart(LocalDate.of(2025, 10, 1))
                .periodEnd(LocalDate.of(2025, 12, 31))
                .totalRevenueGoal(new BigDecimal("40000.00"))
                .isLocked(true)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findHistoricalGoals(eq(testBusiness.getId()), any(LocalDate.class)))
                .thenReturn(List.of(historicalGoal));

        List<GoalReportResponse> result = goalService.getHistoricalReport("test@example.com", testBusiness.getId());

        assertThat(result).hasSize(1);
        verify(goalRepository).findHistoricalGoals(eq(testBusiness.getId()), any(LocalDate.class));
    }

    // ==================== getGoalsSummary Tests ====================

    @Test
    void shouldGetGoalsSummaryWithActiveGoal() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByBusinessIdOrderByPeriodStartDesc(testBusiness.getId()))
                .thenReturn(List.of(testGoal));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("15000.00")));

        List<ActiveGoalSummaryResponse> result = goalService.getGoalsSummary("test@example.com", testBusiness.getId());

        assertThat(result).hasSize(1);
        ActiveGoalSummaryResponse summary = result.get(0);
        assertThat(summary.id()).isEqualTo(testGoal.getId());
        assertThat(summary.name()).isEqualTo("Meta Q1 2026");
        assertThat(summary.categoriesTotal()).isEqualTo(2);
    }

    @Test
    void shouldCalculateDaysRemainingCorrectly() {
        // Goal in the future
        Goal futureGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Future Goal")
                .periodStart(LocalDate.now().plusDays(10))
                .periodEnd(LocalDate.now().plusDays(40))
                .totalRevenueGoal(new BigDecimal("50000.00"))
                .isLocked(false)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByBusinessIdOrderByPeriodStartDesc(testBusiness.getId()))
                .thenReturn(List.of(futureGoal));

        List<ActiveGoalSummaryResponse> result = goalService.getGoalsSummary("test@example.com", testBusiness.getId());

        assertThat(result.get(0).daysRemaining()).isEqualTo("Sin iniciar");
    }

    @Test
    void shouldShowExpiredGoal() {
        // Goal in the past
        Goal expiredGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Expired Goal")
                .periodStart(LocalDate.now().minusDays(90))
                .periodEnd(LocalDate.now().minusDays(1))
                .totalRevenueGoal(new BigDecimal("50000.00"))
                .isLocked(true)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByBusinessIdOrderByPeriodStartDesc(testBusiness.getId()))
                .thenReturn(List.of(expiredGoal));

        List<ActiveGoalSummaryResponse> result = goalService.getGoalsSummary("test@example.com", testBusiness.getId());

        assertThat(result.get(0).daysRemaining()).isEqualTo("Vencida");
    }

    // ==================== getGoalReport Tests ====================

    @Test
    void shouldGetGoalReport() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("15000.00")));

        GoalReportResponse result = goalService.getGoalReport("test@example.com", testBusiness.getId(), testGoal.getId());

        assertThat(result.id()).isEqualTo(testGoal.getId());
        assertThat(result.name()).isEqualTo("Meta Q1 2026");
        assertThat(result.categoryTargets()).hasSize(2);
        assertThat(result.totalRevenueGoal()).isEqualByComparingTo(new BigDecimal("50000.00"));
        verify(goalRepository).findByIdAndBusinessId(testGoal.getId(), testBusiness.getId());
    }

    @Test
    void shouldCalculateAchievementPercentageCorrectly() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        // 15000 actual / 30000 target = 50%
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), eq("Electrónica"), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("15000.00")));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), eq("Ropa"), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("10000.00")));

        GoalReportResponse result = goalService.getGoalReport("test@example.com", testBusiness.getId(), testGoal.getId());

        GoalCategoryTargetResponse electronicaTarget = result.categoryTargets().stream()
                .filter(t -> t.categoryName().equals("Electrónica"))
                .findFirst()
                .orElseThrow();

        assertThat(electronicaTarget.achievement()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ==================== createGoal Tests ====================

    @Test
    void shouldCreateGoalSuccessfully() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("30000.00")),
                new CategoryTargetRequest(category2.getId(), new BigDecimal("20000.00"))
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Meta Q2 2026",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of());
        when(businessRepository.findById(testBusiness.getId()))
                .thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category1, category2));
        when(goalRepository.save(any(Goal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        GoalResponse result = goalService.createGoal("test@example.com", testBusiness.getId(), request);

        assertThat(result.name()).isEqualTo("Meta Q2 2026");
        assertThat(result.categoryTargets()).hasSize(2);
        verify(goalRepository, times(2)).save(any(Goal.class));
    }

    @Test
    void shouldThrowExceptionWhenPeriodStartAfterEnd() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("30000.00"))
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Invalid Goal",
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 4, 1), // end before start
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));

        assertThatThrownBy(() -> goalService.createGoal("test@example.com", testBusiness.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La fecha de inicio debe ser anterior a la fecha de fin");
    }

    @Test
    void shouldThrowExceptionWhenGoalsOverlap() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("30000.00"))
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Overlapping Goal",
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 4, 30),
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of(testGoal)); // existing overlapping goal

        assertThatThrownBy(() -> goalService.createGoal("test@example.com", testBusiness.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Ya existe una meta en el período especificado. Los períodos no pueden superponerse.");
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotFound() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("30000.00")),
                new CategoryTargetRequest(UUID.randomUUID(), new BigDecimal("20000.00")) // non-existent category
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Goal with invalid category",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of());
        when(businessRepository.findById(testBusiness.getId()))
                .thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category1)); // only one category found

        assertThatThrownBy(() -> goalService.createGoal("test@example.com", testBusiness.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Una o más categorías no existen");
    }

    @Test
    void shouldThrowExceptionWhenCategoryNotBelongToBusiness() {
        Business anotherBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Another Business")
                .joinCode("XYZ12345")
                .build();

        Category foreignCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Foreign Category")
                .business(anotherBusiness) // belongs to different business
                .build();

        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(foreignCategory.getId(), new BigDecimal("30000.00"))
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Goal with foreign category",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of());
        when(businessRepository.findById(testBusiness.getId()))
                .thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(foreignCategory));

        assertThatThrownBy(() -> goalService.createGoal("test@example.com", testBusiness.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Una o más categorías no pertenecen al negocio");
    }

    @Test
    void shouldThrowExceptionWhenBusinessNotFoundInCreate() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("30000.00"))
        );

        CreateGoalRequest request = new CreateGoalRequest(
                "Meta Q2 2026",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30),
                new BigDecimal("50000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of());
        when(businessRepository.findById(testBusiness.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.createGoal("test@example.com", testBusiness.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Negocio no encontrado");
    }

    // ==================== updateGoal Tests ====================

    @Test
    void shouldUpdateGoalSuccessfully() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("35000.00")),
                new CategoryTargetRequest(category2.getId(), new BigDecimal("25000.00"))
        );

        UpdateGoalRequest request = new UpdateGoalRequest(
                "Updated Meta Q1 2026",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("60000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of(testGoal)); // only itself
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category1, category2));
        when(goalRepository.save(any(Goal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(goalRepository.saveAndFlush(any(Goal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        GoalResponse result = goalService.updateGoal("test@example.com", testBusiness.getId(), testGoal.getId(), request);

        assertThat(result.name()).isEqualTo("Updated Meta Q1 2026");
        verify(goalRepository).saveAndFlush(any(Goal.class));
        verify(goalRepository).save(any(Goal.class));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingFinishedGoal() {
        Goal finishedGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Finished Goal")
                .periodStart(LocalDate.of(2025, 10, 1))
                .periodEnd(LocalDate.of(2025, 12, 31))
                .totalRevenueGoal(new BigDecimal("50000.00"))
                .isLocked(true)
                .build();

        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("35000.00"))
        );

        UpdateGoalRequest request = new UpdateGoalRequest(
                "Updated Goal",
                LocalDate.of(2025, 10, 1),
                LocalDate.of(2025, 12, 31),
                new BigDecimal("60000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(finishedGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(finishedGoal));

        assertThatThrownBy(() -> goalService.updateGoal("test@example.com", testBusiness.getId(), finishedGoal.getId(), request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("No se puede editar una meta que ya finalizó");
    }

    @Test
    void shouldThrowExceptionWhenGoalNotFoundInUpdate() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("35000.00"))
        );

        UpdateGoalRequest request = new UpdateGoalRequest(
                "Updated Goal",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 3, 31),
                new BigDecimal("60000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.updateGoal("test@example.com", testBusiness.getId(), testGoal.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Meta no encontrada");
    }

    @Test
    void shouldAllowUpdateWhenOverlappingWithSelf() {
        List<CategoryTargetRequest> categoryTargets = List.of(
                new CategoryTargetRequest(category1.getId(), new BigDecimal("35000.00"))
        );

        UpdateGoalRequest request = new UpdateGoalRequest(
                "Updated Meta",
                LocalDate.of(2026, 1, 15), // modified dates but still overlapping
                LocalDate.of(2026, 3, 15),
                new BigDecimal("60000.00"),
                categoryTargets
        );

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        when(goalRepository.findOverlappingGoals(eq(testBusiness.getId()), any(), any()))
                .thenReturn(List.of(testGoal)); // overlaps with itself
        when(categoryRepository.findAllById(any()))
                .thenReturn(List.of(category1));
        when(goalRepository.save(any(Goal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(goalRepository.saveAndFlush(any(Goal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        GoalResponse result = goalService.updateGoal("test@example.com", testBusiness.getId(), testGoal.getId(), request);

        assertThat(result.name()).isEqualTo("Updated Meta");
        verify(goalRepository).save(any(Goal.class));
    }

    // ==================== deleteGoal Tests ====================

    @Test
    void shouldDeleteGoalSuccessfully() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));

        goalService.deleteGoal("test@example.com", testBusiness.getId(), testGoal.getId());

        verify(goalRepository).delete(testGoal);
    }

    @Test
    void shouldDeleteFinishedGoal() {
        Goal finishedGoal = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Finished Goal")
                .periodStart(LocalDate.of(2025, 10, 1))
                .periodEnd(LocalDate.of(2025, 12, 31))
                .totalRevenueGoal(new BigDecimal("50000.00"))
                .isLocked(true)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(finishedGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(finishedGoal));

        goalService.deleteGoal("test@example.com", testBusiness.getId(), finishedGoal.getId());

        verify(goalRepository).delete(finishedGoal);
    }

    @Test
    void shouldThrowExceptionWhenGoalNotFoundInDelete() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> goalService.deleteGoal("test@example.com", testBusiness.getId(), testGoal.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Meta no encontrada");
    }

    // ==================== Achievement Calculation Tests ====================

    @Test
    void shouldCalculateZeroAchievementWhenNoRevenue() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), any(), any(), any()))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        GoalReportResponse result = goalService.getGoalReport("test@example.com", testBusiness.getId(), testGoal.getId());

        assertThat(result.totalAchievement()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldCalculateOverHundredPercentAchievement() {
        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(testGoal.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(testGoal));
        // Actual revenue exceeds target
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), eq("Electrónica"), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("45000.00"))); // 150% of 30000
        when(saleRepository.calculateRevenueByCategoryAndDateRange(any(), eq("Ropa"), any(), any()))
                .thenReturn(Optional.of(new BigDecimal("30000.00"))); // 150% of 20000

        GoalReportResponse result = goalService.getGoalReport("test@example.com", testBusiness.getId(), testGoal.getId());

        // Total: 75000 / 50000 = 150%
        assertThat(result.totalAchievement()).isGreaterThan(new BigDecimal("100"));
    }

    @Test
    void shouldHandleZeroTargetGracefully() {
        Goal goalWithZeroTarget = Goal.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Zero Target Goal")
                .periodStart(LocalDate.of(2026, 1, 1))
                .periodEnd(LocalDate.of(2026, 3, 31))
                .totalRevenueGoal(BigDecimal.ZERO) // Zero target
                .isLocked(false)
                .build();

        when(userRepository.findByEmailIgnoreCase("test@example.com"))
                .thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(testBusiness.getId(), testUser.getId()))
                .thenReturn(Optional.of(activeMembership));
        when(goalRepository.findByIdAndBusinessId(goalWithZeroTarget.getId(), testBusiness.getId()))
                .thenReturn(Optional.of(goalWithZeroTarget));

        GoalReportResponse result = goalService.getGoalReport("test@example.com", testBusiness.getId(), goalWithZeroTarget.getId());

        // Achievement should be zero when target is zero
        assertThat(result.totalAchievement()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
