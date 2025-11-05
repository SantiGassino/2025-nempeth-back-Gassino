package com.nempeth.korven.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "goal")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Goal {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_goal_business"))
    private Business business;

    @Column(name = "name", nullable = false, columnDefinition = "text")
    private String name;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "total_revenue_goal", precision = 12, scale = 2)
    private BigDecimal totalRevenueGoal;

    @Column(name = "is_locked", nullable = false)
    @Builder.Default
    private Boolean isLocked = false;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private Set<GoalCategoryTarget> categoryTargets = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (isLocked == null) isLocked = false;
    }

    public void addCategoryTarget(GoalCategoryTarget target) {
        categoryTargets.add(target);
        target.setGoal(this);
    }

    public void removeCategoryTarget(GoalCategoryTarget target) {
        categoryTargets.remove(target);
        target.setGoal(null);
    }

    public boolean isPeriodActive() {
        LocalDate now = LocalDate.now();
        return !now.isBefore(periodStart) && !now.isAfter(periodEnd);
    }

    public boolean isPeriodFinished() {
        return LocalDate.now().isAfter(periodEnd);
    }
}
