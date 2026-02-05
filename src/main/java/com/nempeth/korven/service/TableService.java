package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.constants.TableStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.Reservation;
import com.nempeth.korven.persistence.entity.TableEntity;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.ReservationRepository;
import com.nempeth.korven.persistence.repository.TableRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.CreateTableRequest;
import com.nempeth.korven.rest.dto.TableOccupancyStatsResponse;
import com.nempeth.korven.rest.dto.TableResponse;
import com.nempeth.korven.rest.dto.UpdateTableRequest;
import com.nempeth.korven.scheduler.ReservationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationScheduler reservationScheduler;
    
    private static final int RESERVATION_LOCK_MINUTES = 45;

    @Transactional
    public UUID createTable(String userEmail, UUID businessId, CreateTableRequest request) {
        validateUserIsOwner(userEmail, businessId);

        if (tableRepository.existsByBusinessIdAndTableCode(businessId, request.tableCode())) {
            throw new IllegalArgumentException("Ya existe una mesa con el código: " + request.tableCode());
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        TableEntity table = TableEntity.builder()
                .business(business)
                .tableCode(request.tableCode())
                .capacity(request.capacity())
                .sector(request.sector())
                .status(TableStatus.FREE)
                .build();

        table = tableRepository.save(table);
        return table.getId();
    }

    @Transactional(readOnly = true)
    public List<TableResponse> getAllTables(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);

        return tableRepository.findByBusinessIdOrderByTableCodeAsc(businessId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTableById(String userEmail, UUID businessId, UUID tableId) {
        validateUserBusinessAccess(userEmail, businessId);

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        return mapToResponse(table);
    }

    @Transactional
    public void updateTable(String userEmail, UUID businessId, UUID tableId, UpdateTableRequest request) {
        validateUserIsOwner(userEmail, businessId);

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        // Solo se puede editar si la mesa está en FREE
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalArgumentException("Solo se pueden editar los datos de mesas en estado Libre");
        }
        
        // Verificar si hay una reserva próxima (45 min)
        if (hasUpcomingReservation(tableId)) {
            throw new IllegalArgumentException(
                "No se puede editar esta mesa. Pronto iniciar\u00e1 una reserva (menos de 45 minutos)"
            );
        }

        if (request.tableCode() != null && !request.tableCode().equals(table.getTableCode())) {
            if (tableRepository.existsByBusinessIdAndTableCode(businessId, request.tableCode())) {
                throw new IllegalArgumentException("Ya existe una mesa con el código: " + request.tableCode());
            }
            table.setTableCode(request.tableCode());
        }

        if (request.capacity() != null) {
            table.setCapacity(request.capacity());
        }

        if (request.sector() != null) {
            table.setSector(request.sector());
        }

        tableRepository.save(table);
    }

    @Transactional
    public void updateTableStatus(String userEmail, UUID businessId, UUID tableId, TableStatus newStatus) {
        BusinessMembership membership = validateUserBusinessAccessAndGetMembership(userEmail, businessId);

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        // Validar transición de estado
        validateStatusTransition(table.getStatus(), newStatus);
        
        // Si intenta cambiar a OCCUPIED, verificar que no haya reserva próxima
        if (newStatus == TableStatus.OCCUPIED && hasUpcomingReservation(tableId)) {
            throw new IllegalArgumentException(
                "No se puede ocupar esta mesa manualmente. Pronto iniciar\u00e1 una reserva (menos de 45 minutos)"
            );
        }

        table.setStatus(newStatus);
        tableRepository.save(table);

        // Procesar reservas de esta mesa si hay alguna próxima
        reservationScheduler.processReservationsForTable(tableId);
    }

    @Transactional
    public void updateTableCapacity(String userEmail, UUID businessId, UUID tableId, Integer newCapacity) {
        // Tanto OWNER como EMPLOYEE pueden cambiar capacidad
        validateUserBusinessAccess(userEmail, businessId);

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        // Solo se puede cambiar la capacidad si la mesa está en FREE
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalArgumentException(
                "Solo se puede cambiar la capacidad de mesas en estado FREE. Estado actual: " + table.getStatus()
            );
        }

        table.setCapacity(newCapacity);
        tableRepository.save(table);
    }

    @Transactional
    public void deleteTable(String userEmail, UUID businessId, UUID tableId) {
        validateUserIsOwner(userEmail, businessId);

        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        // Solo se puede eliminar si está en FREE
        if (table.getStatus() != TableStatus.FREE) {
            throw new IllegalArgumentException("Solo se pueden eliminar mesas en estado Libre");
        }

        tableRepository.delete(table);
    }

    @Transactional(readOnly = true)
    public TableOccupancyStatsResponse getOccupancyStats(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);

        List<TableEntity> tables = tableRepository.findByBusinessIdOrderByTableCodeAsc(businessId);

        int total = tables.size();
        long free = tables.stream().filter(t -> t.getStatus() == TableStatus.FREE).count();
        long reserved = tables.stream().filter(t -> t.getStatus() == TableStatus.RESERVED).count();
        long occupied = tables.stream().filter(t -> t.getStatus() == TableStatus.OCCUPIED).count();

        double occupancyRate = total > 0 ? ((double)(reserved + occupied) / total) * 100 : 0;

        return new TableOccupancyStatsResponse(
                total,
                (int)free,
                (int)reserved,
                (int)occupied,
                Math.round(occupancyRate * 100.0) / 100.0
        );
    }

    private void validateStatusTransition(TableStatus currentStatus, TableStatus newStatus) {
        // No se puede cambiar a RESERVED manualmente - solo por scheduler
        if (newStatus == TableStatus.RESERVED) {
            throw new IllegalArgumentException(
                "No se puede cambiar manualmente a Reservada. El estado Reservada se asigna automáticamente 45 minutos antes del inicio de una reserva."
            );
        }

        // Validaciones de transiciones lógicas
        if (currentStatus == TableStatus.RESERVED && newStatus == TableStatus.OCCUPIED) {
            // OK - cliente llegó
            return;
        }

        if (currentStatus == TableStatus.OCCUPIED && newStatus == TableStatus.FREE) {
            // OK - cliente se fue
            return;
        }

        if (currentStatus == TableStatus.FREE && newStatus == TableStatus.OCCUPIED) {
            // OK - cliente sin reserva se sentó
            return;
        }

        if (currentStatus == TableStatus.RESERVED && newStatus == TableStatus.FREE) {
            // OK - cancelar reserva o no-show
            return;
        }

        if (currentStatus == newStatus) {
            throw new IllegalArgumentException("La mesa ya está en estado " + translateStatus(newStatus));
        }

        throw new IllegalArgumentException(
            String.format("Transición inválida: no se puede cambiar de %s a %s", 
                translateStatus(currentStatus), translateStatus(newStatus))
        );
    }
    
    /**
     * Verifica si una mesa tiene una reserva PENDING que inicia en los próximos 45 minutos
     */
    private boolean hasUpcomingReservation(UUID tableId) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime upcomingTime = now.plusMinutes(RESERVATION_LOCK_MINUTES);
        
        List<Reservation> upcomingReservations = reservationRepository.findUpcomingReservationsForTable(
            tableId, now, upcomingTime
        );
        
        return !upcomingReservations.isEmpty();
    }

    private TableResponse mapToResponse(TableEntity table) {
        return new TableResponse(
                table.getId(),
                table.getTableCode(),
                table.getCapacity(),
                table.getSector(),
                table.getStatus()
        );
    }

    private void validateUserBusinessAccess(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía no está activa");
        }
    }

    private BusinessMembership validateUserBusinessAccessAndGetMembership(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía no está activa");
        }

        return membership;
    }

    private void validateUserIsOwner(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía no está activa");
        }

        if (membership.getRole() != MembershipRole.OWNER) {
            throw new IllegalArgumentException("Solo los propietarios pueden realizar esta acción");
        }
    }

    /**
     * Traduce el estado de la mesa al español para mensajes de error
     */
    private String translateStatus(TableStatus status) {
        return switch (status) {
            case FREE -> "Libre";
            case RESERVED -> "Reservada";
            case OCCUPIED -> "Ocupada";
        };
    }
}
