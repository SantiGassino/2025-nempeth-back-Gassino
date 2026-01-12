package com.nempeth.korven.utils;

import com.nempeth.korven.persistence.entity.Sale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ScoreCalculator Tests")
class ScoreCalculatorTest {

    @Test
    @DisplayName("Should calculate total revenue correctly")
    void shouldCalculateTotalRevenue() {
        // Given
        List<Sale> sales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("250.50")),
                createSale(new BigDecimal("75.25"))
        );

        // When
        BigDecimal totalRevenue = ScoreCalculator.calculateTotalRevenue(sales);

        // Then
        assertThat(totalRevenue).isEqualByComparingTo(new BigDecimal("425.75"));
    }

    @Test
    @DisplayName("Should return zero for empty sales list")
    void shouldReturnZeroForEmptySalesList() {
        // Given
        List<Sale> sales = List.of();

        // When
        BigDecimal totalRevenue = ScoreCalculator.calculateTotalRevenue(sales);

        // Then
        assertThat(totalRevenue).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate average ticket correctly")
    void shouldCalculateAverageTicket() {
        // Given
        BigDecimal totalRevenue = new BigDecimal("1000.00");
        long transactionCount = 4L;

        // When
        BigDecimal avgTicket = ScoreCalculator.calculateAverageTicket(totalRevenue, transactionCount);

        // Then
        assertThat(avgTicket).isEqualByComparingTo(new BigDecimal("250.00"));
    }

    @Test
    @DisplayName("Should return zero average ticket when no transactions")
    void shouldReturnZeroAverageTicketWhenNoTransactions() {
        // Given
        BigDecimal totalRevenue = new BigDecimal("1000.00");
        long transactionCount = 0L;

        // When
        BigDecimal avgTicket = ScoreCalculator.calculateAverageTicket(totalRevenue, transactionCount);

        // Then
        assertThat(avgTicket).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should round average ticket to 2 decimal places")
    void shouldRoundAverageTicketTo2Decimals() {
        // Given
        BigDecimal totalRevenue = new BigDecimal("100.00");
        long transactionCount = 3L;

        // When
        BigDecimal avgTicket = ScoreCalculator.calculateAverageTicket(totalRevenue, transactionCount);

        // Then
        assertThat(avgTicket).isEqualByComparingTo(new BigDecimal("33.33"));
    }

    @Test
    @DisplayName("Should calculate consistency score for uniform sales")
    void shouldCalculateConsistencyScoreForUniformSales() {
        // Given - All sales have the same amount
        List<Sale> sales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00"))
        );
        BigDecimal avgTicket = new BigDecimal("100.00");

        // When
        BigDecimal consistencyScore = ScoreCalculator.calculateConsistencyScore(sales, avgTicket);

        // Then
        // Perfect consistency should give a score of 100
        assertThat(consistencyScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate consistency score for varied sales")
    void shouldCalculateConsistencyScoreForVariedSales() {
        // Given - Sales with variation
        List<Sale> sales = List.of(
                createSale(new BigDecimal("50.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("150.00")),
                createSale(new BigDecimal("200.00"))
        );
        BigDecimal avgTicket = new BigDecimal("125.00");

        // When
        BigDecimal consistencyScore = ScoreCalculator.calculateConsistencyScore(sales, avgTicket);

        // Then
        // Varied sales should have lower consistency score
        assertThat(consistencyScore).isLessThan(new BigDecimal("100.00"));
        assertThat(consistencyScore).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should return maximum consistency for single sale")
    void shouldReturnMaxConsistencyForSingleSale() {
        // Given
        List<Sale> sales = List.of(createSale(new BigDecimal("100.00")));
        BigDecimal avgTicket = new BigDecimal("100.00");

        // When
        BigDecimal consistencyScore = ScoreCalculator.calculateConsistencyScore(sales, avgTicket);

        // Then
        assertThat(consistencyScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate growth score with positive growth")
    void shouldCalculateGrowthScoreWithPositiveGrowth() {
        // Given
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("150.00")),
                createSale(new BigDecimal("150.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00"))
        );

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        // 50% growth: (300 - 200) / 200 = 0.5 = 50%
        // Score = 50 + 50 = 100
        assertThat(growthScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate growth score with negative growth")
    void shouldCalculateGrowthScoreWithNegativeGrowth() {
        // Given
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("75.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("100.00"))
        );

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        // -25% growth: (75 - 100) / 100 = -0.25 = -25%
        // Score = 50 + (-25) = 25
        assertThat(growthScore).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("Should return neutral score when no previous sales")
    void shouldReturnNeutralScoreWhenNoPreviousSales() {
        // Given
        List<Sale> currentSales = List.of(createSale(new BigDecimal("100.00")));
        List<Sale> previousSales = List.of();

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        assertThat(growthScore).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("Should return maximum growth score when growing from zero")
    void shouldReturnMaxGrowthScoreWhenGrowingFromZero() {
        // Given
        List<Sale> currentSales = List.of(createSale(new BigDecimal("100.00")));
        List<Sale> previousSales = List.of(createSale(BigDecimal.ZERO));

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        assertThat(growthScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should clamp growth score to maximum of 100")
    void shouldClampGrowthScoreToMaximum() {
        // Given - 200% growth
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("300.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("100.00"))
        );

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        // 200% growth should be clamped to 100
        assertThat(growthScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should clamp growth score to minimum of 0")
    void shouldClampGrowthScoreToMinimum() {
        // Given - -60% growth
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("40.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("100.00"))
        );

        // When
        BigDecimal growthScore = ScoreCalculator.calculateGrowthScore(currentSales, previousSales);

        // Then
        // -60% growth: score = 50 + (-60) = -10, clamped to 0
        assertThat(growthScore).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should calculate composite score correctly")
    void shouldCalculateCompositeScore() {
        // Given
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("80.00")),
                createSale(new BigDecimal("80.00"))
        );
        double maxRevenue = 1000.0;
        double maxAvgTicket = 200.0;
        double maxTransactionCount = 10.0;

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        assertThat(compositeScore).isGreaterThan(BigDecimal.ZERO);
        assertThat(compositeScore).isLessThanOrEqualTo(new BigDecimal("100.00"));
        // Should be rounded to 2 decimal places
        assertThat(compositeScore.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return zero composite score for empty current sales")
    void shouldReturnZeroCompositeScoreForEmptyCurrentSales() {
        // Given
        List<Sale> currentSales = List.of();
        List<Sale> previousSales = List.of(createSale(new BigDecimal("100.00")));
        double maxRevenue = 1000.0;
        double maxAvgTicket = 200.0;
        double maxTransactionCount = 10.0;

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        assertThat(compositeScore).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should handle maximum values correctly")
    void shouldHandleMaximumValuesCorrectly() {
        // Given - Sales that are at maximum
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("50.00")),
                createSale(new BigDecimal("50.00"))
        );
        
        // Set maximums to match this business exactly
        double maxRevenue = 500.0;
        double maxAvgTicket = 100.0;
        double maxTransactionCount = 5.0;

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        // With all metrics at maximum and high growth, score should be very high
        assertThat(compositeScore).isGreaterThan(new BigDecimal("90.00"));
    }

    @Test
    @DisplayName("Should verify weights sum to expected total")
    void shouldVerifyWeightsSumToExpectedTotal() {
        // Given - Consistent sales at maximum values
        List<Sale> currentSales = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            currentSales.add(createSale(new BigDecimal("100.00")));
        }
        List<Sale> previousSales = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            previousSales.add(createSale(new BigDecimal("50.00")));
        }

        // Set business as the maximum for all metrics
        double maxRevenue = 1000.0;  // This business has 1000
        double maxAvgTicket = 100.0;  // This business has 100
        double maxTransactionCount = 10.0;  // This business has 10

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        // All normalized metrics should be 100 (at max)
        // Consistency should be 100 (uniform sales)
        // Growth should be 100 (100% growth from 500 to 1000)
        // Composite = (100*0.30) + (100*0.25) + (100*0.20) + (100*0.15) + (100*0.10) = 100
        assertThat(compositeScore).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should throw exception when trying to instantiate utility class")
    void shouldThrowExceptionWhenInstantiating() {
        // When & Then
        assertThatThrownBy(() -> {
            var constructor = ScoreCalculator.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })
        .hasCauseInstanceOf(UnsupportedOperationException.class)
        .cause()
        .hasMessage("Utility class");
    }

    @Test
    @DisplayName("Should handle sales with varying amounts in composite score")
    void shouldHandleSalesWithVaryingAmountsInCompositeScore() {
        // Given
        List<Sale> currentSales = List.of(
                createSale(new BigDecimal("50.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("150.00")),
                createSale(new BigDecimal("200.00")),
                createSale(new BigDecimal("250.00"))
        );
        List<Sale> previousSales = List.of(
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00")),
                createSale(new BigDecimal("100.00"))
        );
        double maxRevenue = 1500.0;
        double maxAvgTicket = 300.0;
        double maxTransactionCount = 20.0;

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        assertThat(compositeScore).isGreaterThan(BigDecimal.ZERO);
        assertThat(compositeScore).isLessThan(new BigDecimal("100.00"));
        // Consistency should be lower due to variation
    }

    @Test
    @DisplayName("Should handle zero maximum values gracefully")
    void shouldHandleZeroMaximumValuesGracefully() {
        // Given
        List<Sale> currentSales = List.of(createSale(new BigDecimal("100.00")));
        List<Sale> previousSales = List.of();
        double maxRevenue = 0.0;
        double maxAvgTicket = 0.0;
        double maxTransactionCount = 0.0;

        // When
        BigDecimal compositeScore = ScoreCalculator.calculateCompositeScore(
                currentSales,
                previousSales,
                maxRevenue,
                maxAvgTicket,
                maxTransactionCount
        );

        // Then
        // Should not crash, and should return a valid score
        assertThat(compositeScore).isNotNull();
        assertThat(compositeScore).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(compositeScore).isLessThanOrEqualTo(new BigDecimal("100.00"));
    }

    /**
     * Helper method to create a mock sale with specified amount
     */
    private Sale createSale(BigDecimal amount) {
        return Sale.builder()
                .id(UUID.randomUUID())
                .totalAmount(amount)
                .build();
    }
}
