package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.CreateReservationRequest;
import com.nempeth.korven.rest.dto.ReservationResponse;
import com.nempeth.korven.rest.dto.UpdateReservationRequest;
import com.nempeth.korven.rest.dto.TableGanttResponse;
import com.nempeth.korven.rest.dto.ReservationAnalyticsResponse;
import com.nempeth.korven.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/businesses/{businessId}/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<?> createReservation(@PathVariable UUID businessId,
                                              @Valid @RequestBody CreateReservationRequest request,
                                              Authentication auth) {
        String userEmail = auth.getName();
        UUID reservationId = reservationService.createReservation(userEmail, businessId, request);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva creada exitosamente",
                "reservationId", reservationId.toString()
        ));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getReservations(
            @PathVariable UUID businessId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime endDate,
            Authentication auth) {
        String userEmail = auth.getName();
        List<ReservationResponse> reservations = reservationService.getReservations(
                userEmail, businessId, startDate, endDate);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ReservationResponse>> getUpcomingReservations(
            @PathVariable UUID businessId,
            Authentication auth) {
        String userEmail = auth.getName();
        List<ReservationResponse> reservations = reservationService.getUpcomingReservations(userEmail, businessId);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/past")
    public ResponseEntity<List<ReservationResponse>> getPastReservations(
            @PathVariable UUID businessId,
            Authentication auth) {
        String userEmail = auth.getName();
        List<ReservationResponse> reservations = reservationService.getPastReservations(userEmail, businessId);
        return ResponseEntity.ok(reservations);
    }

    @GetMapping("/analytics")
    public ResponseEntity<ReservationAnalyticsResponse> getReservationAnalytics(
            @PathVariable UUID businessId,
            Authentication auth) {
        String userEmail = auth.getName();
        ReservationAnalyticsResponse analytics = reservationService.getReservationAnalytics(userEmail, businessId);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/gantt")
    public ResponseEntity<List<TableGanttResponse>> getReservationGanttData(
            @PathVariable UUID businessId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            java.time.LocalDate date,
            Authentication auth) {
        String userEmail = auth.getName();
        List<TableGanttResponse> ganttData = reservationService.getReservationGanttData(
                userEmail, businessId, date);
        return ResponseEntity.ok(ganttData);
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservationById(@PathVariable UUID businessId,
                                                                  @PathVariable UUID reservationId,
                                                                  Authentication auth) {
        String userEmail = auth.getName();
        ReservationResponse reservation = reservationService.getReservationById(userEmail, businessId, reservationId);
        return ResponseEntity.ok(reservation);
    }

    @PutMapping("/{reservationId}")
    public ResponseEntity<?> updateReservation(@PathVariable UUID businessId,
                                              @PathVariable UUID reservationId,
                                              @Valid @RequestBody UpdateReservationRequest request,
                                              Authentication auth) {
        String userEmail = auth.getName();
        reservationService.updateReservation(userEmail, businessId, reservationId, request);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva actualizada exitosamente"
        ));
    }

    @PostMapping("/{reservationId}/start")
    public ResponseEntity<?> startReservation(@PathVariable UUID businessId,
                                             @PathVariable UUID reservationId,
                                             Authentication auth) {
        String userEmail = auth.getName();
        reservationService.startReservation(userEmail, businessId, reservationId);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva iniciada - cliente sentado"
        ));
    }

    @PostMapping("/{reservationId}/complete")
    public ResponseEntity<?> completeReservation(@PathVariable UUID businessId,
                                                @PathVariable UUID reservationId,
                                                Authentication auth) {
        String userEmail = auth.getName();
        reservationService.completeReservation(userEmail, businessId, reservationId);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva finalizada - mesa libre"
        ));
    }

    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<?> cancelReservation(@PathVariable UUID businessId,
                                              @PathVariable UUID reservationId,
                                              Authentication auth) {
        String userEmail = auth.getName();
        reservationService.cancelReservation(userEmail, businessId, reservationId);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva cancelada exitosamente"
        ));
    }

    @PostMapping("/{reservationId}/no-show")
    public ResponseEntity<?> markAsNoShow(@PathVariable UUID businessId,
                                         @PathVariable UUID reservationId,
                                         Authentication auth) {
        String userEmail = auth.getName();
        reservationService.markAsNoShow(userEmail, businessId, reservationId);

        return ResponseEntity.ok(Map.of(
                "message", "Reserva marcada como no-show"
        ));
    }
}
