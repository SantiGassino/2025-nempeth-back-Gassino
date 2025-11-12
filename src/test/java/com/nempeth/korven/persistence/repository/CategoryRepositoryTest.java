package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.constants.CategoryType;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.Category;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("CategoryRepository Tests")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Business testBusiness;
    private Category customCategory;
    private Category staticCategory;

    @BeforeEach
    void setUp() {
        categoryRepository.deleteAll();
        
        testBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode("TEST123")
                .build();
        entityManager.persist(testBusiness);

        customCategory = Category.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Custom Category")
                .type(CategoryType.CUSTOM)
                .displayName("Categoría Personalizada")
                .icon("🔧")
                .build();
        entityManager.persist(customCategory);

        staticCategory = Category.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Static Category")
                .type(CategoryType.STATIC)
                .displayName("Categoría Estática")
                .icon("📦")
                .build();
        entityManager.persist(staticCategory);
        
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find categories by business ID")
    void shouldFindCategoriesByBusinessId() {
        List<Category> categories = categoryRepository.findByBusinessId(testBusiness.getId());

        assertThat(categories).hasSize(2);
        assertThat(categories).extracting(Category::getName)
                .containsExactlyInAnyOrder("Custom Category", "Static Category");
    }

    @Test
    @DisplayName("Should return empty list when no categories for business")
    void shouldReturnEmptyListWhenNoCategoriesForBusiness() {
        UUID nonExistentBusinessId = UUID.randomUUID();
        List<Category> categories = categoryRepository.findByBusinessId(nonExistentBusinessId);
        
        assertThat(categories).isEmpty();
    }

    @Test
    @DisplayName("Should find categories by business ID and type CUSTOM")
    void shouldFindCategoriesByBusinessIdAndTypeCustom() {
        List<Category> categories = categoryRepository.findByBusinessIdAndType(
                testBusiness.getId(), 
                CategoryType.CUSTOM
        );

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Custom Category");
        assertThat(categories.get(0).getType()).isEqualTo(CategoryType.CUSTOM);
    }

    @Test
    @DisplayName("Should find categories by business ID and type STATIC")
    void shouldFindCategoriesByBusinessIdAndTypeStatic() {
        List<Category> categories = categoryRepository.findByBusinessIdAndType(
                testBusiness.getId(), 
                CategoryType.STATIC
        );

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Static Category");
        assertThat(categories.get(0).getType()).isEqualTo(CategoryType.STATIC);
    }

    @Test
    @DisplayName("Should return empty list when no categories of specified type")
    void shouldReturnEmptyListWhenNoCategoriesOfType() {
        Business anotherBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Another Business")
                .joinCode("OTHER123")
                .build();
        entityManager.persist(anotherBusiness);
        entityManager.flush();

        List<Category> categories = categoryRepository.findByBusinessIdAndType(
                anotherBusiness.getId(), 
                CategoryType.CUSTOM
        );

        assertThat(categories).isEmpty();
    }

    @Test
    @DisplayName("Should check if category exists by business ID and name ignoring case")
    void shouldCheckIfCategoryExistsByBusinessIdAndNameIgnoreCase() {
        boolean existsExact = categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "Custom Category"
        );
        boolean existsLower = categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "custom category"
        );
        boolean existsUpper = categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "CUSTOM CATEGORY"
        );

        assertThat(existsExact).isTrue();
        assertThat(existsLower).isTrue();
        assertThat(existsUpper).isTrue();
    }

    @Test
    @DisplayName("Should return false when category name does not exist in business")
    void shouldReturnFalseWhenCategoryNameDoesNotExistInBusiness() {
        boolean exists = categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "Non Existent Category"
        );
        
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should save category with all fields")
    void shouldSaveCategoryWithAllFields() {
        Category newCategory = Category.builder()
                .business(testBusiness)
                .name("New Category")
                .type(CategoryType.CUSTOM)
                .displayName("Nueva Categoría")
                .icon("🎯")
                .build();

        Category saved = categoryRepository.save(newCategory);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("New Category");
        assertThat(saved.getType()).isEqualTo(CategoryType.CUSTOM);
        assertThat(saved.getDisplayName()).isEqualTo("Nueva Categoría");
        assertThat(saved.getIcon()).isEqualTo("🎯");
    }

    @Test
    @DisplayName("Should update existing category")
    void shouldUpdateExistingCategory() {
        customCategory.setName("Updated Category");
        customCategory.setDisplayName("Categoría Actualizada");
        categoryRepository.save(customCategory);
        entityManager.flush();
        entityManager.clear();

        Category found = categoryRepository.findById(customCategory.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Updated Category");
        assertThat(found.getDisplayName()).isEqualTo("Categoría Actualizada");
    }

    @Test
    @DisplayName("Should delete category")
    void shouldDeleteCategory() {
        categoryRepository.delete(customCategory);
        entityManager.flush();

        assertThat(categoryRepository.findById(customCategory.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should find multiple custom categories by business")
    void shouldFindMultipleCustomCategoriesByBusiness() {
        Category anotherCustom = Category.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Another Custom")
                .type(CategoryType.CUSTOM)
                .displayName("Otra Personalizada")
                .icon("⚡")
                .build();
        entityManager.persist(anotherCustom);
        entityManager.flush();

        List<Category> categories = categoryRepository.findByBusinessIdAndType(
                testBusiness.getId(), 
                CategoryType.CUSTOM
        );

        assertThat(categories).hasSize(2);
        assertThat(categories).extracting(Category::getName)
                .containsExactlyInAnyOrder("Custom Category", "Another Custom");
    }

    @Test
    @DisplayName("Should not find category from different business")
    void shouldNotFindCategoryFromDifferentBusiness() {
        Business otherBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Other Business")
                .joinCode("OTHER456")
                .build();
        entityManager.persist(otherBusiness);
        entityManager.flush();

        boolean exists = categoryRepository.existsByBusinessIdAndNameIgnoreCase(
                otherBusiness.getId(), 
                "Custom Category"
        );

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should find categories with different types")
    void shouldFindCategoriesWithDifferentTypes() {
        List<Category> allCategories = categoryRepository.findByBusinessId(testBusiness.getId());
        
        assertThat(allCategories).hasSize(2);
        assertThat(allCategories)
                .extracting(Category::getType)
                .containsExactlyInAnyOrder(CategoryType.CUSTOM, CategoryType.STATIC);
    }
}
