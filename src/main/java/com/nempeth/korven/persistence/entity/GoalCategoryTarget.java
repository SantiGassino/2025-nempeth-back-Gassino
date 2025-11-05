package com.nempeth.korven.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "goal_category_target",
       uniqueConstraints = @UniqueConstraint(name = "uk_gct_goal_category",
                                            columnNames = {"goal_id", "category_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalCategoryTarget {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "goal_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_gct_goal"))
    private Goal goal;

    // No FK constraint - allows category deletion while preserving historical data
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "category_name", nullable = false, columnDefinition = "text")
    private String categoryName;

    @Column(name = "revenue_target", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueTarget;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
