package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.persistence.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {
    
    List<Goal> findByBusinessIdOrderByPeriodStartDesc(UUID businessId);
    
    Optional<Goal> findByIdAndBusinessId(UUID id, UUID businessId);
    
    @Query("SELECT g FROM Goal g WHERE g.business.id = :businessId " +
           "AND ((g.periodStart BETWEEN :start AND :end) " +
           "OR (g.periodEnd BETWEEN :start AND :end) " +
           "OR (g.periodStart <= :start AND g.periodEnd >= :end))")
    List<Goal> findOverlappingGoals(@Param("businessId") UUID businessId,
                                     @Param("start") LocalDate start,
                                     @Param("end") LocalDate end);
    
    @Query("SELECT g FROM Goal g WHERE g.business.id = :businessId " +
           "AND g.periodStart <= :date AND g.periodEnd >= :date")
    Optional<Goal> findActiveGoalByDate(@Param("businessId") UUID businessId,
                                        @Param("date") LocalDate date);
    
    @Query("SELECT g FROM Goal g WHERE g.business.id = :businessId " +
           "AND g.periodEnd < :date ORDER BY g.periodEnd DESC")
    List<Goal> findHistoricalGoals(@Param("businessId") UUID businessId,
                                    @Param("date") LocalDate date);
}
