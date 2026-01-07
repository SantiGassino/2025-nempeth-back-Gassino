package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.CreateSaleItemRequest;
import com.nempeth.korven.rest.dto.SaleItemResponse;
import com.nempeth.korven.service.SaleItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/businesses/{businessId}/sales/{saleId}/items")
@RequiredArgsConstructor
public class SaleItemController {

    private final SaleItemService saleItemService;

    @PostMapping
    public ResponseEntity<?> addItemToSale(@PathVariable UUID businessId,
                                          @PathVariable UUID saleId,
                                          @Valid @RequestBody CreateSaleItemRequest request,
                                          Authentication auth) {
        String userEmail = auth.getName();
        UUID itemId = saleItemService.addItemToSale(userEmail, businessId, saleId, request);
        
        return ResponseEntity.ok(Map.of(
                "message", "Item agregado exitosamente",
                "itemId", itemId.toString()
        ));
    }

    @GetMapping
    public ResponseEntity<List<SaleItemResponse>> getSaleItems(@PathVariable UUID businessId,
                                                              @PathVariable UUID saleId,
                                                              Authentication auth) {
        String userEmail = auth.getName();
        List<SaleItemResponse> items = saleItemService.getSaleItems(userEmail, businessId, saleId);
        return ResponseEntity.ok(items);
    }
}
