package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.constants.ReservationStatus;
import com.nempeth.korven.constants.TableStatus;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.Reservation;
import com.nempeth.korven.persistence.entity.TableEntity;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.ReservationRepository;
import com.nempeth.korven.persistence.repository.TableRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.CreateReservationRequest;
import com.nempeth.korven.rest.dto.ReservationResponse;
import com.nempeth.korven.rest.dto.TableSimpleResponse;
import com.nempeth.korven.rest.dto.UpdateReservationRequest;
import com.nempeth.korven.rest.dto.ReservationGanttSlot;
import com.nempeth.korven.rest.dto.TableGanttResponse;
import com.nempeth.korven.rest.dto.ReservationAnalyticsResponse;
import com.nempeth.korven.scheduler.ReservationScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final TableRepository tableRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ReservationScheduler reservationScheduler;

    private static final long MAX_RESERVATION_HOURS = 12;

    @Transactional
    public UUID createReservation(String userEmail, UUID businessId, CreateReservationRequest request) {
        validateUserBusinessAccess(userEmail, businessId);

        // VALIDACIÓN 1: Fechas no pueden estar en el pasado
        OffsetDateTime now = OffsetDateTime.now();
        if (request.startDateTime().isBefore(now)) {
            throw new IllegalArgumentException("La fecha de inicio no puede estar en el pasado");
        }

        // VALIDACIÓN 2: startDateTime debe ser antes que endDateTime
        if (!request.startDateTime().isBefore(request.endDateTime())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }

        // VALIDACIÓN 3: Duración máxima de 12 horas
        Duration duration = Duration.between(request.startDateTime(), request.endDateTime());
        if (duration.toHours() > MAX_RESERVATION_HOURS) {
            throw new IllegalArgumentException(
                    String.format("La duración máxima de una reserva es %d horas", MAX_RESERVATION_HOURS)
            );
        }

        // Obtener todas las mesas solicitadas
        Set<TableEntity> tables = new HashSet<>();
        for (UUID tableId : request.tableIds()) {
            TableEntity table = tableRepository.findById(tableId)
                    .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + tableId));

            if (!table.getBusiness().getId().equals(businessId)) {
                throw new IllegalArgumentException("La mesa " + table.getTableCode() + " no pertenece a este negocio");
            }

            tables.add(table);
        }

        // VALIDACIÓN 4: Suma de capacidades >= party size (salvo override manual)
        int totalCapacity = tables.stream().mapToInt(TableEntity::getCapacity).sum();
        if (!Boolean.TRUE.equals(request.forced()) && request.partySize() > totalCapacity) {
            String tableCodes = tables.stream()
                    .map(TableEntity::getTableCode)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    String.format("La capacidad total de las mesas [%s] es %d personas, no suficiente para %d. " +
                                    "Aplique la opcion de forzar si desea continuar con esa capacidad.",
                            tableCodes, totalCapacity, request.partySize())
            );
        }

        // VALIDACIÓN 5: No permitir overlapping en NINGUNA de las mesas (sin excepciones)
        // Incluye buffer de 20 minutos ANTES y 5 minutos DESPUÉS para separación entre reservas
        // - Buffer ANTES: evita que se cree una reserva justo antes de otra (tiempo de preparación)
        // - Buffer DESPUÉS: evita que se cree una reserva justo después de otra (tiempo de limpieza)
        OffsetDateTime bufferStartTime = request.startDateTime().minusMinutes(20);
        OffsetDateTime bufferEndTime = request.endDateTime().plusMinutes(5);
        
        for (TableEntity table : tables) {
            List<Reservation> overlapping = reservationRepository.findOverlappingReservationsForTable(
                    table.getId(),
                    bufferStartTime,
                    bufferEndTime
            );

            if (!overlapping.isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("La mesa %s ya tiene reservas en conflicto para ese horario. " +
                                        "Se requiere 20 minutos antes y 5 minutos después de separación entre reservas.",
                                table.getTableCode())
                );
            }
        }

        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        Reservation reservation = Reservation.builder()
                .business(tables.iterator().next().getBusiness())
                .tables(tables)
                .customerName(request.customerName())
                .customerContact(request.customerContact())
                .customerDocument(request.customerDocument())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .partySize(request.partySize())
                .status(ReservationStatus.PENDING)
                .forced(Boolean.TRUE.equals(request.forced()))
                .createdByUser(user)
                .notes(request.notes())
                .build();

        reservation = reservationRepository.save(reservation);

        // Procesar inmediatamente si la reserva inicia en menos de 20 minutos
        reservationScheduler.processReservationIfUpcoming(reservation.getId());

        return reservation.getId();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations(String userEmail, UUID businessId,
                                                     OffsetDateTime startDate, OffsetDateTime endDate) {
        validateUserBusinessAccess(userEmail, businessId);

        List<Reservation> reservations;
        if (startDate != null && endDate != null) {
            reservations = reservationRepository.findByBusinessIdAndDateRange(businessId, startDate, endDate);
        } else {
            reservations = reservationRepository.findByBusinessIdOrderByStartDateTimeDesc(businessId);
        }

        return reservations.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse getReservationById(String userEmail, UUID businessId, UUID reservationId) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        return mapToResponse(reservation);
    }

    @Transactional
    public void updateReservation(String userEmail, UUID businessId, UUID reservationId, UpdateReservationRequest request) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Solo se pueden modificar reservas pendientes");
        }

        // Determinar las mesas finales (nuevas o existentes)
        Set<TableEntity> finalTables = reservation.getTables();
        if (request.tableIds() != null && !request.tableIds().isEmpty()) {
            Set<TableEntity> newTables = new HashSet<>();
            for (UUID tableId : request.tableIds()) {
                TableEntity table = tableRepository.findById(tableId)
                        .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada: " + tableId));

                if (!table.getBusiness().getId().equals(businessId)) {
                    throw new IllegalArgumentException("La mesa " + table.getTableCode() + " no pertenece a este negocio");
                }

                newTables.add(table);
            }
            finalTables = newTables;
        }

        // Determinar fechas finales (nuevas o existentes)
        OffsetDateTime newStart = request.startDateTime() != null ? request.startDateTime() : reservation.getStartDateTime();
        OffsetDateTime newEnd = request.endDateTime() != null ? request.endDateTime() : reservation.getEndDateTime();

        // Validar cambios de fechas
        if (request.startDateTime() != null || request.endDateTime() != null) {
            // Validar que no estén en el pasado
            OffsetDateTime now = OffsetDateTime.now();
            if (newStart.isBefore(now)) {
                throw new IllegalArgumentException("La fecha de inicio no puede estar en el pasado");
            }

            // Validar orden
            if (!newStart.isBefore(newEnd)) {
                throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
            }

            // Validar duración máxima
            Duration duration = Duration.between(newStart, newEnd);
            if (duration.toHours() > MAX_RESERVATION_HOURS) {
                throw new IllegalArgumentException(
                        String.format("La duración máxima de una reserva es %d horas", MAX_RESERVATION_HOURS)
                );
            }
        }

        // VALIDACIÓN DE SOLAPAMIENTO: Si cambian fechas o mesas, validar conflictos
        // Solo considera conflictos con reservas en estado PENDING o IN_PROGRESS
        // Excluye la reserva actual que se está editando
        if (request.startDateTime() != null || request.endDateTime() != null || 
            (request.tableIds() != null && !request.tableIds().isEmpty())) {
            
            OffsetDateTime bufferStartTime = newStart.minusMinutes(20);
            OffsetDateTime bufferEndTime = newEnd.plusMinutes(5);
            
            for (TableEntity table : finalTables) {
                List<Reservation> overlapping = reservationRepository.findOverlappingReservationsForTableExcluding(
                        table.getId(),
                        bufferStartTime,
                        bufferEndTime,
                        reservationId
                );

                if (!overlapping.isEmpty()) {
                    throw new IllegalArgumentException(
                            String.format("La mesa %s ya tiene reservas en conflicto para ese horario. " +
                                            "Se requiere 20 minutos antes y 5 minutos después de separación entre reservas.",
                                    table.getTableCode())
                    );
                }
            }
        }

        // Aplicar cambios de mesas
        if (request.tableIds() != null && !request.tableIds().isEmpty()) {
            // Liberar mesas antiguas que no están en la nueva selección
            // Solo si estaban RESERVED por esta reserva específica
            Set<TableEntity> oldTables = reservation.getTables();
            for (TableEntity oldTable : oldTables) {
                if (!finalTables.contains(oldTable) && oldTable.getStatus() == TableStatus.RESERVED) {
                    oldTable.setStatus(TableStatus.FREE);
                    tableRepository.save(oldTable);
                }
            }

            // Las nuevas mesas NO cambian a RESERVED inmediatamente
            // El scheduler las cambiará 20 minutos antes del inicio
            reservation.setTables(finalTables);
        }

        // Aplicar cambios de fechas
        if (request.startDateTime() != null || request.endDateTime() != null) {
            reservation.setStartDateTime(newStart);
            reservation.setEndDateTime(newEnd);
        }

        if (request.customerName() != null) {
            reservation.setCustomerName(request.customerName());
        }

        if (request.customerContact() != null) {
            reservation.setCustomerContact(request.customerContact());
        }

        if (request.customerDocument() != null) {
            reservation.setCustomerDocument(request.customerDocument());
        }

        if (request.partySize() != null) {
            reservation.setPartySize(request.partySize());
        }

        if (request.notes() != null) {
            reservation.setNotes(request.notes());
        }

        reservationRepository.save(reservation);

        // Procesar inmediatamente si la reserva inicia en menos de 20 minutos
        reservationScheduler.processReservationIfUpcoming(reservationId);
    }

    @Transactional
    public void startReservation(String userEmail, UUID businessId, UUID reservationId) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Solo se pueden iniciar reservas pendientes");
        }

        // Validar que esté dentro del rango permitido: desde 15 min antes hasta endDateTime
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime allowedStartTime = reservation.getStartDateTime().minusMinutes(15);
        OffsetDateTime allowedEndTime = reservation.getEndDateTime();

        if (now.isBefore(allowedStartTime)) {
            throw new IllegalArgumentException(
                    "Solo se puede iniciar la reserva desde 15 minutos antes de la hora de inicio. " +
                    "Hora permitida: " + allowedStartTime
            );
        }

        if (now.isAfter(allowedEndTime)) {
            throw new IllegalArgumentException(
                    "No se puede iniciar la reserva después de su hora de finalización. " +
                    "La reserva expiró a las: " + allowedEndTime
            );
        }

        reservation.setStatus(ReservationStatus.IN_PROGRESS);

        // Marcar todas las mesas como ocupadas
        for (TableEntity table : reservation.getTables()) {
            table.setStatus(TableStatus.OCCUPIED);
            tableRepository.save(table);
        }

        reservationRepository.save(reservation);
    }

    @Transactional
    public void completeReservation(String userEmail, UUID businessId, UUID reservationId) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        if (reservation.getStatus() != ReservationStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Solo se pueden completar reservas en curso");
        }

        reservation.setStatus(ReservationStatus.COMPLETED);

        // Liberar todas las mesas
        for (TableEntity table : reservation.getTables()) {
            table.setStatus(TableStatus.FREE);
            tableRepository.save(table);
        }

        reservationRepository.save(reservation);
    }

    @Transactional
    public void cancelReservation(String userEmail, UUID businessId, UUID reservationId) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new IllegalArgumentException("No se puede cancelar una reserva completada");
        }

        // Validar que no haya pasado la hora de finalización (después de eso, no se puede cambiar el estado)
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isAfter(reservation.getEndDateTime())) {
            throw new IllegalArgumentException(
                    "No se puede cancelar la reserva después de su hora de finalización. " +
                    "La reserva finalizó a las: " + reservation.getEndDateTime()
            );
        }

        reservation.setStatus(ReservationStatus.CANCELLED);

        // Liberar todas las mesas reservadas u ocupadas por esta reserva
        for (TableEntity table : reservation.getTables()) {
            if (table.getStatus() == TableStatus.RESERVED || table.getStatus() == TableStatus.OCCUPIED) {
                table.setStatus(TableStatus.FREE);
                tableRepository.save(table);
            }
        }

        reservationRepository.save(reservation);
    }

    @Transactional
    public void markAsNoShow(String userEmail, UUID businessId, UUID reservationId) {
        validateUserBusinessAccess(userEmail, businessId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reserva no encontrada"));

        if (!reservation.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La reserva no pertenece a este negocio");
        }

        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Solo se pueden marcar como no-show las reservas pendientes");
        }

        // Validar que esté dentro del rango permitido: desde startDateTime hasta endDateTime
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime allowedStartTime = reservation.getStartDateTime();
        OffsetDateTime allowedEndTime = reservation.getEndDateTime();

        if (now.isBefore(allowedStartTime)) {
            throw new IllegalArgumentException(
                    "Solo se puede marcar como no-show desde la hora de inicio de la reserva. " +
                    "Hora de inicio: " + allowedStartTime
            );
        }

        if (now.isAfter(allowedEndTime)) {
            throw new IllegalArgumentException(
                    "No se puede marcar como no-show después de la hora de finalización. " +
                    "La reserva expiró a las: " + allowedEndTime
            );
        }

        reservation.setStatus(ReservationStatus.NO_SHOW);

        // Liberar todas las mesas reservadas
        for (TableEntity table : reservation.getTables()) {
            if (table.getStatus() == TableStatus.RESERVED) {
                table.setStatus(TableStatus.FREE);
                tableRepository.save(table);
            }
        }

        reservationRepository.save(reservation);
    }

    /**
     * Obtiene datos de reservas por mesa para un día específico.
     * Ideal para generar diagramas de Gantt diarios.
     * 
     * Incluye todas las reservas que interfieran con el día especificado:
     * - Reservas que empiezan el día anterior y terminan ese día
     * - Reservas que empiezan y terminan ese día
     * - Reservas que empiezan ese día y terminan al día siguiente
     * 
     * @param userEmail Email del usuario
     * @param businessId ID del negocio
     * @param date Día específico a consultar (formato: 2026-02-15)
     * @return Lista de mesas con sus reservas en el día especificado
     */
    @Transactional(readOnly = true)
    public List<TableGanttResponse> getReservationGanttData(String userEmail, UUID businessId, 
                                                            java.time.LocalDate date) {
        validateUserBusinessAccess(userEmail, businessId);

        // Convertir el día a rango completo (00:00:00 hasta 23:59:59.999999999)
        OffsetDateTime startOfDay = date.atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toOffsetDateTime().minusNanos(1);

        // Obtener todas las mesas del negocio
        List<TableEntity> tables = tableRepository.findByBusinessIdOrderByTableCodeAsc(businessId);

        // Obtener todas las reservas que se solapen con este día
        // Incluye: reservas que empiezan antes y terminan durante el día,
        //          reservas que empiezan durante el día,
        //          reservas que empiezan durante el día y terminan después
        List<Reservation> reservations = reservationRepository.findByBusinessIdAndDateRange(businessId, startOfDay, endOfDay);

        // Mapear cada mesa con sus reservas
        return tables.stream()
                .map(table -> {
                    // Filtrar reservas que incluyen esta mesa
                    List<ReservationGanttSlot> tableReservations = reservations.stream()
                            .filter(r -> r.getTables().stream().anyMatch(t -> t.getId().equals(table.getId())))
                            .map(r -> new ReservationGanttSlot(
                                    r.getId(),
                                    r.getCustomerName(),
                                    r.getCustomerDocument(),
                                    r.getStartDateTime(),
                                    r.getEndDateTime(),
                                    r.getPartySize(),
                                    r.getStatus()
                            ))
                            .sorted((a, b) -> a.startDateTime().compareTo(b.startDateTime()))
                            .toList();

                    return new TableGanttResponse(
                            table.getId(),
                            table.getTableCode(),
                            table.getCapacity(),
                            tableReservations
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getUpcomingReservations(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);

        OffsetDateTime now = OffsetDateTime.now();
        List<Reservation> reservations = reservationRepository.findByBusinessIdOrderByStartDateTimeDesc(businessId);

        return reservations.stream()
                .filter(r -> r.getStartDateTime().isAfter(now) || r.getStartDateTime().isEqual(now))
                .sorted(Comparator.comparing(Reservation::getStartDateTime))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> getPastReservations(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);

        OffsetDateTime now = OffsetDateTime.now();
        List<Reservation> reservations = reservationRepository.findByBusinessIdOrderByStartDateTimeDesc(businessId);

        return reservations.stream()
                .filter(r -> r.getStartDateTime().isBefore(now))
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationAnalyticsResponse getReservationAnalytics(String userEmail, UUID businessId) {
        validateUserBusinessAccess(userEmail, businessId);

        List<Reservation> allReservations = reservationRepository.findByBusinessIdOrderByStartDateTimeDesc(businessId);

        // 1. RESUMEN GENERAL
        long total = allReservations.size();
        long pending = allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.PENDING).count();
        long completed = allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.COMPLETED).count();
        long cancelled = allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.CANCELLED).count();
        long noShow = allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.NO_SHOW).count();
        long inProgress = allReservations.stream().filter(r -> r.getStatus() == ReservationStatus.IN_PROGRESS).count();

        double completionRate = total > 0 ? (completed * 100.0) / total : 0.0;
        double noShowRate = total > 0 ? (noShow * 100.0) / total : 0.0;
        double cancellationRate = total > 0 ? (cancelled * 100.0) / total : 0.0;

        ReservationAnalyticsResponse.ReservationSummary summary = new ReservationAnalyticsResponse.ReservationSummary(
                total, pending, completed, cancelled, noShow, inProgress,
                completionRate, noShowRate, cancellationRate
        );

        // 2. UTILIZACIÓN POR MESA
        Map<String, List<Reservation>> reservationsByTable = allReservations.stream()
                .flatMap(r -> r.getTables().stream().map(t -> Map.entry(t.getTableCode(), r)))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        List<ReservationAnalyticsResponse.TableUtilization> tableUtilization = reservationsByTable.entrySet().stream()
                .map(entry -> {
                    String tableCode = entry.getKey();
                    List<Reservation> tableReservations = entry.getValue();

                    long totalReservations = tableReservations.size();
                    long totalMinutes = tableReservations.stream()
                            .mapToLong(r -> Duration.between(r.getStartDateTime(), r.getEndDateTime()).toMinutes())
                            .sum();
                    long totalHours = totalMinutes / 60;
                    long completedRes = tableReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.COMPLETED)
                            .count();
                    long noShows = tableReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.NO_SHOW)
                            .count();

                    // Tasa de utilización basada en reservas completadas vs total
                    double utilizationRate = totalReservations > 0 ? (completedRes * 100.0) / totalReservations : 0.0;

                    return new ReservationAnalyticsResponse.TableUtilization(
                            tableCode, totalReservations, totalHours, completedRes, noShows, utilizationRate
                    );
                })
                .sorted(Comparator.comparing(ReservationAnalyticsResponse.TableUtilization::totalReservations).reversed())
                .toList();

        // 3. CONFIABILIDAD DE CLIENTES
        // Agrupamos por documento (identificador único) en lugar de nombre
        Map<String, List<Reservation>> reservationsByCustomer = allReservations.stream()
                .collect(Collectors.groupingBy(Reservation::getCustomerDocument));

        List<ReservationAnalyticsResponse.ClientReliability> clientReliability = reservationsByCustomer.entrySet().stream()
                .map(entry -> {
                    String customerDocument = entry.getKey();
                    List<Reservation> customerReservations = entry.getValue();

                    // Obtener el nombre más reciente del cliente (por si cambia de nombre)
                    String customerName = customerReservations.stream()
                            .max(Comparator.comparing(Reservation::getCreatedAt))
                            .map(Reservation::getCustomerName)
                            .orElse("Desconocido");

                    String customerContact = customerReservations.stream()
                            .max(Comparator.comparing(Reservation::getCreatedAt))
                            .map(Reservation::getCustomerContact)
                            .filter(c -> c != null && !c.isEmpty())
                            .orElse("");

                    long totalRes = customerReservations.size();
                    long completedRes = customerReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.COMPLETED)
                            .count();
                    long noShows = customerReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.NO_SHOW)
                            .count();
                    long cancellations = customerReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.CANCELLED)
                            .count();

                    // Score: completed=100%, noShow=-50%, cancelled=-25%
                    double reliabilityScore = totalRes > 0 
                            ? ((completedRes * 100.0) - (noShows * 50.0) - (cancellations * 25.0)) / totalRes 
                            : 0.0;

                    return new ReservationAnalyticsResponse.ClientReliability(
                            customerName, customerContact, customerDocument, totalRes, completedRes, noShows, cancellations, reliabilityScore
                    );
                })
                .sorted(Comparator.comparing(ReservationAnalyticsResponse.ClientReliability::totalReservations).reversed())
                .limit(20) // Top 20 clientes
                .toList();

        // 4. ANÁLISIS POR FRANJA HORARIA
        Map<Integer, List<Reservation>> reservationsByHour = allReservations.stream()
                .collect(Collectors.groupingBy(r -> r.getStartDateTime().getHour()));

        List<ReservationAnalyticsResponse.TimeSlotAnalysis> timeSlotAnalysis = reservationsByHour.entrySet().stream()
                .map(entry -> {
                    int hour = entry.getKey();
                    List<Reservation> hourReservations = entry.getValue();

                    long totalRes = hourReservations.size();
                    long noShows = hourReservations.stream()
                            .filter(r -> r.getStatus() == ReservationStatus.NO_SHOW)
                            .count();
                    double noShowRateHour = totalRes > 0 ? (noShows * 100.0) / totalRes : 0.0;
                    double avgPartySize = hourReservations.stream()
                            .mapToInt(Reservation::getPartySize)
                            .average()
                            .orElse(0.0);

                    return new ReservationAnalyticsResponse.TimeSlotAnalysis(
                            hour, totalRes, noShows, noShowRateHour, avgPartySize
                    );
                })
                .sorted(Comparator.comparing(ReservationAnalyticsResponse.TimeSlotAnalysis::hourOfDay))
                .toList();

        // 5. DESPERDICIO DE CAPACIDAD
        // Agrupamos reservas completadas por código de mesa
        Map<String, List<Map.Entry<Integer, Integer>>> capacityByTable = allReservations.stream()
                .filter(r -> r.getStatus() == ReservationStatus.COMPLETED)
                .flatMap(r -> r.getTables().stream()
                        .map(t -> Map.entry(t.getTableCode(), Map.entry(t.getCapacity(), r.getPartySize()))))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));

        List<ReservationAnalyticsResponse.CapacityWaste> capacityWaste = capacityByTable.entrySet().stream()
                .map(entry -> {
                    String tableCode = entry.getKey();
                    List<Map.Entry<Integer, Integer>> capacityData = entry.getValue();

                    int tableCapacity = capacityData.stream()
                            .map(Map.Entry::getKey)
                            .findFirst()
                            .orElse(0);

                    long reservationCount = capacityData.size();
                    double avgPartySize = capacityData.stream()
                            .mapToInt(Map.Entry::getValue)
                            .average()
                            .orElse(0.0);

                    // Porcentaje de desperdicio: capacidad no utilizada
                    double wastePercentage = tableCapacity > 0 
                            ? ((tableCapacity - avgPartySize) * 100.0) / tableCapacity 
                            : 0.0;

                    return new ReservationAnalyticsResponse.CapacityWaste(
                            tableCode, tableCapacity, reservationCount, avgPartySize, wastePercentage
                    );
                })
                .filter(cw -> cw.wastePercentage() > 20) // Solo mostrar si hay más de 20% desperdicio
                .sorted(Comparator.comparing(ReservationAnalyticsResponse.CapacityWaste::wastePercentage).reversed())
                .toList();

        return new ReservationAnalyticsResponse(
                summary, tableUtilization, clientReliability, timeSlotAnalysis, capacityWaste
        );
    }

    private ReservationResponse mapToResponse(Reservation reservation) {
        String createdBy = null;
        if (reservation.getCreatedByUser() != null) {
            User user = reservation.getCreatedByUser();
            createdBy = (user.getName() + " " + user.getLastName()).trim();
            if (createdBy.isEmpty()) {
                createdBy = user.getEmail();
            }
        }

        List<TableSimpleResponse> tablesResponse = reservation.getTables().stream()
                .map(t -> new TableSimpleResponse(t.getId(), t.getTableCode(), t.getCapacity()))
                .toList();

        return new ReservationResponse(
                reservation.getId(),
                tablesResponse,
                reservation.getCustomerName(),
                reservation.getCustomerContact(),
                reservation.getCustomerDocument(),
                reservation.getStartDateTime(),
                reservation.getEndDateTime(),
                reservation.getPartySize(),
                reservation.getStatus(),
                reservation.getForced(),
                createdBy,
                reservation.getCreatedAt(),
                reservation.getNotes()
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
}
