package com.nempeth.korven.service;

import com.nempeth.korven.constants.MembershipRole;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final BusinessRepository businessRepository;
    private final BusinessMembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TableRepository tableRepository;

    @Transactional
    public UUID createSale(String userEmail, UUID businessId) {
        User user = validateUserBusinessAccess(userEmail, businessId);
        
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio no encontrado"));

        // Construir nombre completo del usuario
        String userName = (user.getName() + " " + user.getLastName()).trim();
        if (userName.isEmpty()) {
            userName = user.getEmail();
        }

        // Crear la venta vacía
        Sale sale = Sale.builder()
                .business(business)
                .createdByUser(user)
                .createdByUserName(userName)
                .occurredAt(null)
                .totalAmount(BigDecimal.ZERO)
                .build();

        sale = saleRepository.save(sale);

        return sale.getId();
    }

    @Transactional
    public UUID createSaleForTable(UUID tableId, UUID businessId, UUID createdByUserId, String createdByUserName) {
        TableEntity table = tableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Mesa no encontrada"));

        if (!table.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La mesa no pertenece a este negocio");
        }

        // Verificar si ya existe una orden abierta para esta mesa
        List<Sale> existingOpenSales = saleRepository.findByBusinessIdAndTableIdAndOccurredAtIsNull(businessId, tableId);
        if (!existingOpenSales.isEmpty()) {
            // Ya existe una orden abierta, retornar su ID
            return existingOpenSales.get(0).getId();
        }

        Business business = table.getBusiness();
        User createdByUser = userRepository.findById(createdByUserId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Crear la venta asociada a la mesa con el usuario que la creó
        Sale sale = Sale.builder()
                .business(business)
                .table(table)
                .createdByUser(createdByUser)
                .createdByUserName(createdByUserName)
                .occurredAt(null)
                .totalAmount(BigDecimal.ZERO)
                .build();

        sale = saleRepository.save(sale);

        return sale.getId();
    }

    @Transactional
    public void closeSalesByTable(UUID tableId) {
        List<Sale> openSales = saleRepository.findByTableIdAndOccurredAtIsNull(tableId);
        
        for (Sale sale : openSales) {
            // Recalcular el total desde los items
            List<SaleItem> items = saleItemRepository.findBySaleId(sale.getId());
            BigDecimal totalAmount = items.stream()
                    .map(SaleItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            sale.setOccurredAt(OffsetDateTime.now());
            sale.setTotalAmount(totalAmount);
            saleRepository.save(sale);
        }
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByBusiness(String userEmail, UUID businessId, Boolean open) {
        BusinessMembership membership = validateUserBusinessAccessAndGetMembership(userEmail, businessId);
        
        List<Sale> sales;
        if (membership.getRole() == MembershipRole.OWNER) {
            if (open == null) {
                sales = saleRepository.findByBusinessIdOrderByOccurredAtDesc(businessId);
            } else if (open) {
                sales = saleRepository.findByBusinessIdAndOccurredAtIsNullOrderByIdDesc(businessId);
            } else {
                sales = saleRepository.findByBusinessIdAndOccurredAtIsNotNullOrderByOccurredAtDesc(businessId);
            }
        } else {
            if (open == null) {
                sales = saleRepository.findByBusinessIdAndCreatedByUserIdOrderByOccurredAtDesc(businessId, membership.getUser().getId());
            } else if (open) {
                sales = saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtIsNullOrderByIdDesc(businessId, membership.getUser().getId());
            } else {
                sales = saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtIsNotNullOrderByOccurredAtDesc(businessId, membership.getUser().getId());
            }
        }
        
        return sales.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SaleResponse> getSalesByBusinessAndDateRange(String userEmail, UUID businessId, 
                                                           OffsetDateTime startDate, OffsetDateTime endDate) {
        BusinessMembership membership = validateUserBusinessAccessAndGetMembership(userEmail, businessId);
        
        if (membership.getRole() == MembershipRole.OWNER) {
            return saleRepository.findByBusinessIdAndOccurredAtBetweenOrderByOccurredAtDesc(businessId, startDate, endDate).stream()
                    .map(this::mapToResponse)
                    .toList();
        } else {
            return saleRepository.findByBusinessIdAndCreatedByUserIdAndOccurredAtBetweenOrderByOccurredAtDesc(businessId, membership.getUser().getId(), startDate, endDate).stream()
                    .map(this::mapToResponse)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public SaleResponse getSaleById(String userEmail, UUID businessId, UUID saleId) {
        BusinessMembership membership = validateUserBusinessAccessAndGetMembership(userEmail, businessId);
        
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        
        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La venta no pertenece a este negocio");
        }
        
        if (membership.getRole() == MembershipRole.EMPLOYEE) {
            if (!sale.getCreatedByUser().getId().equals(membership.getUser().getId())) {
                throw new IllegalArgumentException("No tienes permisos para ver esta venta");
            }
        }
        
        return mapToResponse(sale);
    }

    @Transactional
    public void closeSale(String userEmail, UUID businessId, UUID saleId) {
        validateUserBusinessAccess(userEmail, businessId);
        
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        
        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La venta no pertenece a este negocio");
        }
        
        if (sale.getOccurredAt() != null) {
            throw new IllegalArgumentException("La venta ya está cerrada");
        }

        // Validar que si tiene mesa asociada, no se puede cerrar manualmente
        if (sale.getTable() != null) {
            throw new IllegalArgumentException(
                "No se puede cerrar esta orden manualmente. Está asociada a la mesa " + 
                sale.getTable().getTableCode() + ". La orden se cerrará automáticamente cuando la mesa quede libre"
            );
        }
        
        // Recalcular el total desde los items
        List<SaleItem> items = saleItemRepository.findBySaleId(saleId);
        BigDecimal totalAmount = items.stream()
                .map(SaleItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        sale.setOccurredAt(OffsetDateTime.now());
        sale.setTotalAmount(totalAmount);
        saleRepository.save(sale);
    }

    @Transactional
    public void deleteSale(String userEmail, UUID businessId, UUID saleId) {
        validateUserBusinessAccess(userEmail, businessId);

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new IllegalArgumentException("La venta no pertenece a este negocio");
        }

        if (sale.getOccurredAt() != null) {
            throw new IllegalArgumentException("No se puede eliminar una venta que ya está cerrada");
        }

        if (sale.getTable() != null) {
            throw new IllegalArgumentException(
                "No se puede eliminar esta orden. Está asociada a la mesa " +
                sale.getTable().getTableCode() + ". Libere la mesa primero para cerrar la orden automáticamente"
            );
        }

        saleRepository.delete(sale);
    }

    private User validateUserBusinessAccess(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));
        
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía en este negocio no está activa");
        }
        
        return user;
    }

    private BusinessMembership validateUserBusinessAccessAndGetMembership(String userEmail, UUID businessId) {
        User user = userRepository.findByEmailIgnoreCase(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        BusinessMembership membership = membershipRepository.findByBusinessIdAndUserId(businessId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No tienes acceso a este negocio"));
        
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Tu membresía en este negocio no está activa");
        }
        
        return membership;
    }

    private SaleResponse mapToResponse(Sale sale) {
        List<SaleItemResponse> items = saleItemRepository.findBySaleId(sale.getId()).stream()
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

        // Usar el nombre guardado en la venta, con fallback a "Sistema" si no existe
        String createdByUserName = sale.getCreatedByUserName() != null && !sale.getCreatedByUserName().isEmpty()
                ? sale.getCreatedByUserName()
                : "Sistema";

        // Mapear información de la mesa si existe
        SaleTableInfo tableInfo = null;
        if (sale.getTable() != null) {
            tableInfo = SaleTableInfo.builder()
                    .id(sale.getTable().getId())
                    .tableCode(sale.getTable().getTableCode())
                    .build();
        }

        return SaleResponse.builder()
                .id(sale.getId())
                .occurredAt(sale.getOccurredAt())
                .totalAmount(sale.getTotalAmount())
                .createdByUserName(createdByUserName)
                .table(tableInfo)
                .items(items)
                .build();
    }
}