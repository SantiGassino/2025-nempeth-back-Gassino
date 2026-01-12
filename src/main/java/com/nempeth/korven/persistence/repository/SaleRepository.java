package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.persistence.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleRepository extends JpaRepository<Sale, UUID> {
    List<Sale> findByBusinessIdOrderByOccurredAtDesc(UUID businessId);
    
    List<Sale> findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        UUID businessId, 
        OffsetDateTime startDate, 
        OffsetDateTime endDate
    );
    
    // Métodos para filtrar ventas por usuario creador
    List<Sale> findByBusinessIdAndCreatedByUserIdOrderByOccurredAtDesc(UUID businessId, UUID userId);
    
    List<Sale> findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(
        UUID businessId, 
        UUID userId,
        OffsetDateTime startDate, 
        OffsetDateTime endDate
    );
    
    // Métodos para filtrar ventas abiertas/cerradas
    List<Sale> findByBusinessIdAndOccurredAtIsNullOrderByIdDesc(UUID businessId);
    
    List<Sale> findByBusinessIdAndOccurredAtIsNotNullOrderByOccurredAtDesc(UUID businessId);
    
    List<Sale> findByBusinessIdAndCreatedByUserIdAndOccurredAtIsNullOrderByIdDesc(UUID businessId, UUID userId);
    
    List<Sale> findByBusinessIdAndCreatedByUserIdAndOccurredAtIsNotNullOrderByOccurredAtDesc(UUID businessId, UUID userId);
    
    @Query("SELECT s FROM Sale s WHERE s.business.id = :businessId AND s.occurredAt >= :startDate ORDER BY s.occurredAt DESC")
    List<Sale> findRecentSalesForBusiness(@Param("businessId") UUID businessId, @Param("startDate") OffsetDateTime startDate);

    @Query("""
    SELECT YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName, SUM(si.lineTotal)
    FROM Sale s 
    JOIN s.saleItems si 
        WHERE s.business.id = :businessId 
        AND s.occurredAt BETWEEN :startDate AND :endDate
    GROUP BY YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName
    ORDER BY YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName
        """)
    List<Object[]> findMonthlyRevenueByCategory(@Param("businessId") UUID businessId, 
                                               @Param("startDate") OffsetDateTime startDate, 
                                               @Param("endDate") OffsetDateTime endDate);
    
    @Query("""
        SELECT YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName, 
        SUM(si.lineTotal) - SUM(si.unitCost * si.quantity)
        FROM Sale s 
        JOIN s.saleItems si 
        WHERE s.business.id = :businessId 
        AND s.occurredAt BETWEEN :startDate AND :endDate
        GROUP BY YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName
        ORDER BY YEAR(s.occurredAt), MONTH(s.occurredAt), si.categoryName
        """)
    List<Object[]> findMonthlyProfitByCategory(@Param("businessId") UUID businessId, 
                                              @Param("startDate") OffsetDateTime startDate, 
                                              @Param("endDate") OffsetDateTime endDate);
    
    @Query("""
        SELECT YEAR(s.occurredAt), MONTH(s.occurredAt), SUM(s.totalAmount)
        FROM Sale s 
        WHERE s.business.id = :businessId 
        AND s.occurredAt BETWEEN :startDate AND :endDate
        GROUP BY YEAR(s.occurredAt), MONTH(s.occurredAt)
        ORDER BY YEAR(s.occurredAt), MONTH(s.occurredAt)
        """)
    List<Object[]> findMonthlyTotalRevenue(@Param("businessId") UUID businessId, 
                                          @Param("startDate") OffsetDateTime startDate, 
                                          @Param("endDate") OffsetDateTime endDate);
    
    @Query("""
        SELECT YEAR(s.occurredAt), MONTH(s.occurredAt), 
               SUM(si.lineTotal) - SUM(si.unitCost * si.quantity)
        FROM Sale s 
        JOIN s.saleItems si 
        WHERE s.business.id = :businessId 
        AND s.occurredAt BETWEEN :startDate AND :endDate
        GROUP BY YEAR(s.occurredAt), MONTH(s.occurredAt)
        ORDER BY YEAR(s.occurredAt), MONTH(s.occurredAt)
        """)
    List<Object[]> findMonthlyTotalProfit(@Param("businessId") UUID businessId, 
                                         @Param("startDate") OffsetDateTime startDate, 
                                         @Param("endDate") OffsetDateTime endDate);
    
    @Query(value = """
        SELECT COALESCE(SUM(si.line_total), 0)
        FROM sale s 
        JOIN sale_item si ON si.sale_id = s.id
        WHERE s.business_id = :businessId 
        AND si.category_name = :categoryName
        AND s.occurred_at::date BETWEEN :startDate AND :endDate
        """, nativeQuery = true)
    Optional<BigDecimal> calculateRevenueByCategoryAndDateRange(
        @Param("businessId") UUID businessId,
        @Param("categoryName") String categoryName,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}