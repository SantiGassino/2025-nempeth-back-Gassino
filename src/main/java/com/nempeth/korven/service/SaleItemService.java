package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.CreateSaleItemRequest;
import com.nempeth.korven.rest.dto.SaleItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleItemService {

    private final SaleItemRepository saleItemRepository;
    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public UUID addItemToSale(String userEmail, UUID businessId, UUID saleId, CreateSaleItemRequest request) {
        // Validar acceso del usuario al negocio
        validateUserBusinessAccess(userEmail, businessId);
        
        // Validar que la venta pertenece al negocio
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        
        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La venta no pertenece a este negocio");
        }
        
        // Obtener el producto
        Product product = productRepository.findByIdAndBusinessId(request.productId(), businessId)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado en este negocio"));
        
        // Verificar si el producto ya existe en la venta
        var existingItem = saleItemRepository.findBySaleIdAndProductId(saleId, request.productId());
        
        // Si la cantidad es 0, eliminar el item si existe
        if (request.quantity() == 0) {
            if (existingItem.isPresent()) {
                saleItemRepository.delete(existingItem.get());
                return existingItem.get().getId();
            } else {
                throw new IllegalArgumentException("No se puede establecer cantidad 0 para un producto que no está en la venta");
            }
        }
        
        SaleItem saleItem;
        BigDecimal oldLineTotal = BigDecimal.ZERO;
        
        if (existingItem.isPresent()) {
            // Actualizar item existente
            saleItem = existingItem.get();
            oldLineTotal = saleItem.getLineTotal();
            
            // Actualizar los valores
            saleItem.setQuantity(request.quantity());
            saleItem.setUnitPrice(product.getPrice());
            saleItem.setUnitCost(product.getCost());
            saleItem.setLineTotal(product.getPrice().multiply(BigDecimal.valueOf(request.quantity())));
        } else {
            // Crear nuevo item
            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(request.quantity()));
            
            saleItem = SaleItem.builder()
                    .sale(sale)
                    .product(product)
                    .productNameAtSale(product.getName())
                    .categoryName(product.getCategory().getName())
                    .unitPrice(product.getPrice())
                    .unitCost(product.getCost())
                    .quantity(request.quantity())
                    .lineTotal(lineTotal)
                    .build();
        }
        
        saleItem = saleItemRepository.save(saleItem);
        
        return saleItem.getId();
    }

    @Transactional(readOnly = true)
    public List<SaleItemResponse> getSaleItems(String userEmail, UUID businessId, UUID saleId) {
        // Validar acceso del usuario al negocio
        validateUserBusinessAccess(userEmail, businessId);
        
        // Validar que la venta pertenece al negocio
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        
        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La venta no pertenece a este negocio");
        }
        
        // Obtener los items
        return saleItemRepository.findBySaleId(saleId).stream()
                .map(item -> SaleItemResponse.builder()
                        .id(item.getId())
                        .categoryName(item.getCategoryName())
                        .productName(item.getProductNameAtSale())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .unitCost(item.getUnitCost())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }

    private void validateUserBusinessAccess(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));
        
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía en este negocio no está activa");
        }
    }
}
