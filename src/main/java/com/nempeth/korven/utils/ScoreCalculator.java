package com.nempeth.korven.utils;

import com.nempeth.korven.persistence.entity.Sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Utility class for calculating business performance scores
 * Uses a composite scoring system that considers multiple metrics:
 * - Total Revenue (30%)
 * - Average Ticket (25%)
 * - Consistency (20%)
 * - Transaction Volume (15%)
 * - Growth (10%)
 */
public class ScoreCalculator {

    // Weights for composite score calculation (must sum to 1.0)
    private static final double WEIGHT_TOTAL_REVENUE = 0.30;      // 30%
    private static final double WEIGHT_AVG_TICKET = 0.25;         // 25%
    private static final double WEIGHT_CONSISTENCY = 0.20;        // 20%
    private static final double WEIGHT_TRANSACTION_VOLUME = 0.15; // 15%
    private static final double WEIGHT_GROWTH = 0.10;             // 10%

    /**
     * Calculate composite score for a business based on sales data
     * Uses dynamic normalization based on maximum values across all businesses
     * 
     * @param currentMonthSales Sales from current month
     * @param previousMonthSales Sales from previous month (for growth calculation)
     * @param maxRevenue Maximum revenue across all businesses (for normalization)
     * @param maxAvgTicket Maximum average ticket across all businesses (for normalization)
     * @param maxTransactionCount Maximum transaction count across all businesses (for normalization)
     * @return Composite score from 0 to 100
     */
    public static BigDecimal calculateCompositeScore(
            List<Sale> currentMonthSales,
            List<Sale> previousMonthSales,
            double maxRevenue,
            double maxAvgTicket,
            double maxTransactionCount) {

        if (currentMonthSales.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Calculate all metrics
        BigDecimal totalRevenue = calculateTotalRevenue(currentMonthSales);
        long transactionCount = currentMonthSales.size();
        BigDecimal avgTicket = calculateAverageTicket(totalRevenue, transactionCount);
        BigDecimal consistencyScore = calculateConsistencyScore(currentMonthSales, avgTicket);
        BigDecimal growthScore = calculateGrowthScore(currentMonthSales, previousMonthSales);

        // Normalize each metric to 0-100 scale using dynamic maximums
        double normalizedRevenue = normalizeValue(totalRevenue.doubleValue(), 0, maxRevenue);
        double normalizedAvgTicket = normalizeValue(avgTicket.doubleValue(), 0, maxAvgTicket);
        double normalizedConsistency = consistencyScore.doubleValue(); // Already 0-100
        double normalizedVolume = normalizeValue(transactionCount, 0, maxTransactionCount);
        double normalizedGrowth = growthScore.doubleValue(); // Already 0-100

        // Apply weights to calculate final composite score
        double finalScore =
                (normalizedRevenue * WEIGHT_TOTAL_REVENUE) +
                (normalizedAvgTicket * WEIGHT_AVG_TICKET) +
                (normalizedConsistency * WEIGHT_CONSISTENCY) +
                (normalizedVolume * WEIGHT_TRANSACTION_VOLUME) +
                (normalizedGrowth * WEIGHT_GROWTH);

        return BigDecimal.valueOf(finalScore).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate total revenue from sales
     */
    public static BigDecimal calculateTotalRevenue(List<Sale> sales) {
        return sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate average ticket (revenue per transaction)
     */
    public static BigDecimal calculateAverageTicket(BigDecimal totalRevenue, long transactionCount) {
        if (transactionCount == 0) {
            return BigDecimal.ZERO;
        }
        return totalRevenue.divide(
                BigDecimal.valueOf(transactionCount),
                2,
                RoundingMode.HALF_UP
        );
    }

    /**
     * Calculate consistency score based on standard deviation
     * Higher score = more consistent sales amounts
     * Score ranges from 0 to 100
     */
    public static BigDecimal calculateConsistencyScore(List<Sale> sales, BigDecimal avgTicket) {
        if (sales.size() < 2) {
            return BigDecimal.valueOf(100); // Maximum consistency with few sales
        }

        // Calculate variance
        double variance = sales.stream()
                .mapToDouble(sale -> {
                    double diff = sale.getTotalAmount().subtract(avgTicket).doubleValue();
                    return diff * diff;
                })
                .average()
                .orElse(0.0);

        double stdDev = Math.sqrt(variance);

        // Calculate coefficient of variation (CV)
        double cv = (avgTicket.doubleValue() != 0) ? (stdDev / avgTicket.doubleValue()) : 0;

        // Convert to score (0-100, where 100 is maximum consistency)
        // Lower CV = higher consistency
        double consistencyScore = Math.max(0, 100 - (cv * 100));

        return BigDecimal.valueOf(consistencyScore).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calculate growth score by comparing current vs previous period
     * Positive growth increases score, negative growth decreases it
     * Score ranges from 0 to 100
     */
    public static BigDecimal calculateGrowthScore(
            List<Sale> currentMonthSales,
            List<Sale> previousMonthSales) {

        if (previousMonthSales.isEmpty()) {
            return BigDecimal.valueOf(50); // Neutral score if no previous data
        }

        BigDecimal currentRevenue = calculateTotalRevenue(currentMonthSales);
        BigDecimal previousRevenue = calculateTotalRevenue(previousMonthSales);

        if (previousRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100); // Maximum growth from zero
        }

        // Calculate percentage growth
        BigDecimal growth = currentRevenue.subtract(previousRevenue)
                .divide(previousRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        // Normalize to 0-100 scale
        // Growth of 50% or more = score of 100
        // Growth of 0% = score of 50
        // Negative growth decreases score proportionally
        BigDecimal normalizedGrowth = growth.add(BigDecimal.valueOf(50));
        
        // Clamp between 0 and 100
        return normalizedGrowth
                .max(BigDecimal.ZERO)
                .min(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Normalize a value to 0-100 scale based on min and max bounds
     */
    private static double normalizeValue(double value, double min, double max) {
        if (max <= min) return 0.0; // Avoid division by zero
        if (value >= max) return 100.0;
        if (value <= min) return 0.0;
        return ((value - min) / (max - min)) * 100.0;
    }

    // Private constructor to prevent instantiation
    private ScoreCalculator() {
        throw new UnsupportedOperationException("Utility class");
    }
}
