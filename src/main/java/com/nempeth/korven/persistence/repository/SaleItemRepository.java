package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.persistence.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SaleItemRepository extends JpaRepository<SaleItem, UUID> {
    List<SaleItem> findBySaleId(UUID saleId);
    
    List<SaleItem> findByProductId(UUID productId);
    
    Optional<SaleItem> findBySaleIdAndProductId(UUID saleId, UUID productId);
}