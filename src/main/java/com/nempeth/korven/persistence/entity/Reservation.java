package com.nempeth.korven.persistence.entity;

import com.nempeth.korven.constants.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "reservation",
       indexes = {
           @Index(name = "ix_reservation_business_datetime",
                  columnList = "business_id, start_datetime DESC"),
           @Index(name = "ix_reservation_start_end",
                  columnList = "start_datetime, end_datetime")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_reservation_business"))
    private Business business;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "reservation_table",
        joinColumns = @JoinColumn(name = "reservation_id", foreignKey = @ForeignKey(name = "fk_res_table_reservation")),
        inverseJoinColumns = @JoinColumn(name = "table_id", foreignKey = @ForeignKey(name = "fk_res_table_table"))
    )
    private Set<TableEntity> tables;

    @Column(name = "customer_name", nullable = false, columnDefinition = "text")
    private String customerName;

    @Column(name = "customer_contact", nullable = false, columnDefinition = "text")
    private String customerContact;

    @Column(name = "customer_document", nullable = false, columnDefinition = "text")
    private String customerDocument;

    @Column(name = "start_datetime", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime startDateTime;

    @Column(name = "end_datetime", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime endDateTime;

    @Column(name = "party_size", nullable = false)
    private Integer partySize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "text")
    @Builder.Default
    private ReservationStatus status = ReservationStatus.PENDING;

    @Column(name = "forced", nullable = false)
    @Builder.Default
    private Boolean forced = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id",
                foreignKey = @ForeignKey(name = "fk_reservation_user"))
    private User createdByUser;

    @Column(name = "created_at", columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
