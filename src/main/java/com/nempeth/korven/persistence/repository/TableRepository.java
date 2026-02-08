package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.constants.TableStatus;
import com.nempeth.korven.persistence.entity.TableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableRepository extends JpaRepository<TableEntity, UUID> {
    
    List<TableEntity> findByBusinessIdOrderByTableCodeAsc(UUID businessId);
    
    Optional<TableEntity> findByBusinessIdAndTableCode(UUID businessId, String tableCode);
    
    boolean existsByBusinessIdAndTableCode(UUID businessId, String tableCode);
    
    @Query("SELECT t FROM TableEntity t WHERE t.business.id = :businessId AND t.status = :status")
    List<TableEntity> findByBusinessIdAndStatus(@Param("businessId") UUID businessId,
                                                @Param("status") TableStatus status);
}
