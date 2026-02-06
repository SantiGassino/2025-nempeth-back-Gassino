package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.constants.ReservationStatus;
import com.nempeth.korven.persistence.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    
    List<Reservation> findByBusinessIdAndStatusOrderByStartDateTimeDesc(UUID businessId, ReservationStatus status);
    
    List<Reservation> findByBusinessIdOrderByStartDateTimeDesc(UUID businessId);
    
    @Query("""
        SELECT r FROM Reservation r 
        JOIN r.tables t
        WHERE t.id = :tableId 
        AND r.status IN ('PENDING', 'IN_PROGRESS')
        AND NOT (r.endDateTime <= :startTime OR r.startDateTime >= :endTime)
        """)
    List<Reservation> findOverlappingReservationsForTable(
        @Param("tableId") UUID tableId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime);
    
    @Query("""
        SELECT r FROM Reservation r 
        JOIN r.tables t
        WHERE t.id = :tableId 
        AND r.id != :excludeReservationId
        AND r.status IN ('PENDING', 'IN_PROGRESS')
        AND NOT (r.endDateTime <= :startTime OR r.startDateTime >= :endTime)
        """)
    List<Reservation> findOverlappingReservationsForTableExcluding(
        @Param("tableId") UUID tableId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime,
        @Param("excludeReservationId") UUID excludeReservationId);
    
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.business.id = :businessId 
        AND r.startDateTime < :endDate
        AND r.endDateTime > :startDate
        ORDER BY r.startDateTime DESC
        """)
    List<Reservation> findByBusinessIdAndDateRange(
        @Param("businessId") UUID businessId,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate);
    
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.business.id = :businessId 
        AND r.status = :status
        AND DATE(r.startDateTime) = DATE(:date)
        ORDER BY r.startDateTime ASC
        """)
    List<Reservation> findByBusinessIdAndStatusAndDate(
        @Param("businessId") UUID businessId,
        @Param("status") ReservationStatus status,
        @Param("date") OffsetDateTime date);
    
    /**
     * Busca reservas PENDING de una mesa específica que inicien en los próximos X minutos
     */
    @Query("""
        SELECT r FROM Reservation r 
        JOIN r.tables t
        WHERE t.id = :tableId 
        AND r.status = 'PENDING'
        AND r.startDateTime > :now
        AND r.startDateTime <= :upcomingTime
        """)
    List<Reservation> findUpcomingReservationsForTable(
        @Param("tableId") UUID tableId,
        @Param("now") OffsetDateTime now,
        @Param("upcomingTime") OffsetDateTime upcomingTime);
    
    /**
     * Busca todas las reservas PENDING que inicien en los próximos X minutos
     */
    @Query("""
        SELECT r FROM Reservation r 
        WHERE r.status = 'PENDING'
        AND r.startDateTime > :now
        AND r.startDateTime <= :upcomingTime
        """)
    List<Reservation> findAllUpcomingReservations(
        @Param("now") OffsetDateTime now,
        @Param("upcomingTime") OffsetDateTime upcomingTime);
    
    /**
     * Busca reservas IN_PROGRESS activas de una mesa específica
     */
    @Query("""
        SELECT r FROM Reservation r 
        JOIN r.tables t
        WHERE t.id = :tableId 
        AND r.status = 'IN_PROGRESS'
        """)
    List<Reservation> findActiveReservationsForTable(
        @Param("tableId") UUID tableId);
    
    /**
     * Busca reservas PENDING o IN_PROGRESS de una mesa específica
     */
    @Query("""
        SELECT r FROM Reservation r 
        JOIN r.tables t
        WHERE t.id = :tableId 
        AND r.status IN ('PENDING', 'IN_PROGRESS')
        """)
    List<Reservation> findPendingOrActiveReservationsForTable(
        @Param("tableId") UUID tableId);
}
