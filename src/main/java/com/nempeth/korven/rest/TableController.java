package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.*;
import com.nempeth.korven.service.TableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/businesses/{businessId}/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @PostMapping
    public ResponseEntity<?> createTable(@PathVariable UUID businessId,
                                        @Valid @RequestBody CreateTableRequest request,
                                        Authentication auth) {
        String userEmail = auth.getName();
        UUID tableId = tableService.createTable(userEmail, businessId, request);

        return ResponseEntity.ok(Map.of(
                "message", "Mesa creada exitosamente",
                "tableId", tableId.toString()
        ));
    }

    @GetMapping
    public ResponseEntity<List<TableResponse>> getAllTables(@PathVariable UUID businessId,
                                                           Authentication auth) {
        String userEmail = auth.getName();
        List<TableResponse> tables = tableService.getAllTables(userEmail, businessId);
        return ResponseEntity.ok(tables);
    }

    @GetMapping("/{tableId}")
    public ResponseEntity<TableResponse> getTableById(@PathVariable UUID businessId,
                                                      @PathVariable UUID tableId,
                                                      Authentication auth) {
        String userEmail = auth.getName();
        TableResponse table = tableService.getTableById(userEmail, businessId, tableId);
        return ResponseEntity.ok(table);
    }

    @GetMapping("/stats")
    public ResponseEntity<TableOccupancyStatsResponse> getOccupancyStats(@PathVariable UUID businessId,
                                                                         Authentication auth) {
        String userEmail = auth.getName();
        TableOccupancyStatsResponse stats = tableService.getOccupancyStats(userEmail, businessId);
        return ResponseEntity.ok(stats);
    }

    @PutMapping("/{tableId}")
    public ResponseEntity<?> updateTable(@PathVariable UUID businessId,
                                        @PathVariable UUID tableId,
                                        @Valid @RequestBody UpdateTableRequest request,
                                        Authentication auth) {
        String userEmail = auth.getName();
        tableService.updateTable(userEmail, businessId, tableId, request);

        return ResponseEntity.ok(Map.of(
                "message", "Mesa actualizada exitosamente"
        ));
    }

    @PatchMapping("/{tableId}/status")
    public ResponseEntity<?> updateTableStatus(@PathVariable UUID businessId,
                                              @PathVariable UUID tableId,
                                              @Valid @RequestBody UpdateTableStatusRequest request,
                                              Authentication auth) {
        String userEmail = auth.getName();
        tableService.updateTableStatus(userEmail, businessId, tableId, request.status());

        return ResponseEntity.ok(Map.of(
                "message", "Estado de mesa actualizado exitosamente"
        ));
    }

    @PatchMapping("/{tableId}/capacity")
    public ResponseEntity<?> updateTableCapacity(@PathVariable UUID businessId,
                                                @PathVariable UUID tableId,
                                                @Valid @RequestBody UpdateTableCapacityRequest request,
                                                Authentication auth) {
        String userEmail = auth.getName();
        tableService.updateTableCapacity(userEmail, businessId, tableId, request.capacity());

        return ResponseEntity.ok(Map.of(
                "message", "Capacidad de mesa actualizada exitosamente"
        ));
    }

    @DeleteMapping("/{tableId}")
    public ResponseEntity<?> deleteTable(@PathVariable UUID businessId,
                                        @PathVariable UUID tableId,
                                        Authentication auth) {
        String userEmail = auth.getName();
        tableService.deleteTable(userEmail, businessId, tableId);

        return ResponseEntity.ok(Map.of(
                "message", "Mesa inactivada exitosamente"
        ));
    }

    @PostMapping("/{tableId}/reactivate")
    public ResponseEntity<?> reactivateTable(@PathVariable UUID businessId,
                                             @PathVariable UUID tableId,
                                             Authentication auth) {
        String userEmail = auth.getName();
        tableService.reactivateTable(userEmail, businessId, tableId);

        return ResponseEntity.ok(Map.of(
                "message", "Mesa reactivada exitosamente"
        ));
    }
}
