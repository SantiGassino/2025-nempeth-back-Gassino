package com.nempeth.korven.scheduler;

import com.nempeth.korven.constants.ReservationStatus;
import com.nempeth.korven.constants.TableStatus;
import com.nempeth.korven.persistence.entity.Reservation;
import com.nempeth.korven.persistence.entity.TableEntity;
import com.nempeth.korven.persistence.repository.ReservationRepository;
import com.nempeth.korven.persistence.repository.TableRepository;
import com.nempeth.korven.service.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Set;

/**
 * Scheduler que automáticamente cambia el estado de las mesas a RESERVED
 * cuando una reserva está por iniciar (20 minutos antes).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservationScheduler {

    private static final int RESERVATION_LOCK_MINUTES = 20;
    
    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final SaleService saleService;

    /**
     * Se ejecuta cada 5 minutos para verificar reservas próximas
     * y marcar las mesas correspondientes como RESERVED
     */
    @Scheduled(fixedRate = 300000) // 5 minutos = 300,000 ms
    @Transactional
    public void processUpcomingReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingTime = now.plusMinutes(RESERVATION_LOCK_MINUTES);
        
        List<Reservation> upcomingReservations = reservationRepository.findAllUpcomingReservations(now, upcomingTime);
        
        if (!upcomingReservations.isEmpty()) {
            log.info("Procesando {} reservas próximas a iniciar", upcomingReservations.size());
            
            for (Reservation reservation : upcomingReservations) {
                processReservationTables(reservation);
            }
        }
    }

    /**
     * Sincronización completa de estados de mesas:
     * 1. Marca como RESERVED las mesas con reservas próximas (< 20 min)
     * 2. Libera mesas RESERVED que ya no tienen reservas próximas asociadas
     * 
     * Este método es público para poder ser llamado desde el endpoint de sincronización manual.
     */
    @Transactional
    public void syncTableStatuses() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingTime = now.plusMinutes(RESERVATION_LOCK_MINUTES);
        
        // 1. Obtener todas las reservas próximas (< 20 min)
        List<Reservation> upcomingReservations = reservationRepository.findAllUpcomingReservations(now, upcomingTime);
        
        // 2. Construir set de IDs de mesas que DEBERÍAN estar en RESERVED
        Set<UUID> tablesShouldBeReserved = new java.util.HashSet<>();
        for (Reservation reservation : upcomingReservations) {
            for (TableEntity table : reservation.getTables()) {
                tablesShouldBeReserved.add(table.getId());
            }
        }
        
        // 3. Marcar como RESERVED las mesas que deberían estarlo
        for (Reservation reservation : upcomingReservations) {
            processReservationTables(reservation);
        }
        
        // 4. Liberar mesas que están RESERVED pero NO deberían estarlo
        List<TableEntity> allReservedTables = tableRepository.findByStatus(TableStatus.RESERVED);
        for (TableEntity table : allReservedTables) {
            if (!tablesShouldBeReserved.contains(table.getId())) {
                log.info("Liberando mesa {} que estaba RESERVED sin reserva próxima", table.getTableCode());
                table.setStatus(TableStatus.FREE);
                tableRepository.save(table);
            }
        }
        
        log.info("Sincronización completada: {} mesas deberían estar RESERVED", tablesShouldBeReserved.size());
    }

    /**
     * Procesa una reserva específica para cambiar el estado de sus mesas a RESERVED
     * si la reserva está por iniciar (menos de 20 minutos).
     * Este método es público para poder ser llamado desde otros servicios.
     */
    @Transactional
    public void processReservationIfUpcoming(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingTime = now.plusMinutes(RESERVATION_LOCK_MINUTES);

        // Solo procesar si la reserva inicia en los próximos 20 minutos
        if (reservation.getStartDateTime().isAfter(now) && 
            reservation.getStartDateTime().isBefore(upcomingTime)) {
            log.info("Procesando reserva {} inmediatamente (inicia en menos de 20 minutos)", reservationId);
            processReservationTables(reservation);
        }
    }

    /**
     * Procesa todas las reservas que incluyan una mesa específica.
     * Útil cuando se cambia manualmente el estado de una mesa.
     */
    @Transactional
    public void processReservationsForTable(UUID tableId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingTime = now.plusMinutes(RESERVATION_LOCK_MINUTES);

        List<Reservation> upcomingReservations = reservationRepository.findAllUpcomingReservations(now, upcomingTime);

        for (Reservation reservation : upcomingReservations) {
            boolean hasTable = reservation.getTables().stream()
                    .anyMatch(table -> table.getId().equals(tableId));
            
            if (hasTable) {
                log.info("Procesando reserva {} por cambio en mesa", reservation.getId());
                processReservationTables(reservation);
            }
        }
    }
    
    private void processReservationTables(Reservation reservation) {
        for (TableEntity table : reservation.getTables()) {
            // Solo cambiar a RESERVED si la mesa está FREE u OCCUPIED
            // Si ya está RESERVED (por otra reserva o proceso), no tocar
            if (table.getStatus() == TableStatus.FREE || table.getStatus() == TableStatus.OCCUPIED) {
                log.info("Cambiando mesa {} a RESERVED para reserva {} que inicia en {} minutos",
                    table.getTableCode(),
                    reservation.getId(),
                    java.time.Duration.between(OffsetDateTime.now(), reservation.getStartDateTime()).toMinutes());
                
                // Si estaba OCCUPIED, cerrar la orden antes de pasar a RESERVED
                if (table.getStatus() == TableStatus.OCCUPIED) {
                    saleService.closeSalesByTable(table.getId());
                }
                
                table.setStatus(TableStatus.RESERVED);
                tableRepository.save(table);
            }
        }
    }
    
    /**
     * Se ejecuta cada hora para marcar como NO_SHOW las reservas PENDING expiradas
     * y liberar las mesas correspondientes.
     * Nota: Las reservas IN_PROGRESS pueden permanecer abiertas indefinidamente
     * hasta que el usuario las cierre manualmente.
     */
    @Scheduled(fixedRate = 3600000) // 1 hora = 3,600,000 ms
    @Transactional
    public void cleanupExpiredReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        
        // Buscar SOLO reservas PENDING cuyo endDateTime ya pasó (no tocar IN_PROGRESS)
        List<Reservation> expiredPendingReservations = reservationRepository.findAll()
            .stream()
            .filter(r -> r.getStatus() == ReservationStatus.PENDING)
            .filter(r -> r.getEndDateTime().isBefore(now))
            .toList();
        
        if (!expiredPendingReservations.isEmpty()) {
            log.warn("Encontradas {} reservas PENDING expiradas. Marcando como NO_SHOW...", expiredPendingReservations.size());
            
            for (Reservation reservation : expiredPendingReservations) {
                log.warn("Marcando reserva {} como NO_SHOW (expiró sin iniciar)", reservation.getId());
                reservation.setStatus(ReservationStatus.NO_SHOW);
                reservationRepository.save(reservation);
                
                // Liberar las mesas
                for (TableEntity table : reservation.getTables()) {
                    if (table.getStatus() != TableStatus.FREE) {
                        table.setStatus(TableStatus.FREE);
                        tableRepository.save(table);
                    }
                }
            }
        }
    }
}
