package com.nempeth.korven.persistence.entity;

import com.nempeth.korven.constants.TableStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "restaurant_table",
       uniqueConstraints = @UniqueConstraint(name = "uq_table_business_code",
                                            columnNames = {"business_id", "table_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableEntity {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_table_business"))
    private Business business;

    @Column(name = "table_code", nullable = false, length = 20)
    private String tableCode;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "sector", nullable = false, columnDefinition = "text")
    private String sector;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    @Builder.Default
    private TableStatus status = TableStatus.FREE;

    // Removed @OneToMany relationship - now Reservation has @ManyToMany with tables
    // If needed, can query reservations via ReservationRepository

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
