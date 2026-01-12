package com.nempeth.korven.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "sale",
       indexes = @Index(name = "ix_sale_business_time", 
                       columnList = "business_id, occurred_at DESC"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sale_business"))
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id",
                foreignKey = @ForeignKey(name = "fk_sale_user"))
    private User createdByUser;

    @Column(name = "created_by_user_name", columnDefinition = "text")
    private String createdByUserName;

    @Column(name = "occurred_at", columnDefinition = "timestamptz")
    private OffsetDateTime occurredAt;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<SaleItem> saleItems;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}