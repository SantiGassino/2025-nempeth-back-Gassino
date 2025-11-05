package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.persistence.entity.GoalCategoryTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalCategoryTargetRepository extends JpaRepository<GoalCategoryTarget, UUID> {
    
    List<GoalCategoryTarget> findByGoalId(UUID goalId);
    
    Optional<GoalCategoryTarget> findByGoalIdAndCategoryId(UUID goalId, UUID categoryId);
    
    @Query("SELECT gct FROM GoalCategoryTarget gct " +
           "WHERE gct.goal.id = :goalId AND gct.categoryId = :categoryId")
    boolean existsByGoalIdAndCategoryId(@Param("goalId") UUID goalId,
                                        @Param("categoryId") UUID categoryId);
    
    void deleteByGoalId(UUID goalId);

    @Modifying
    @Query("UPDATE GoalCategoryTarget gct SET gct.categoryName = :newName WHERE gct.categoryId = :categoryId")
    int updateCategoryNameByCategoryId(@Param("categoryId") UUID categoryId, @Param("newName") String newName);
}
