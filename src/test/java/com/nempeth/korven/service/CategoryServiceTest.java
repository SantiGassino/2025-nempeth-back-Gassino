package com.nempeth.korven.service;

import com.nempeth.korven.constants.CategoryType;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.BusinessMembership;
import com.nempeth.korven.persistence.entity.Category;
import com.nempeth.korven.persistence.entity.User;
import com.nempeth.korven.persistence.repository.BusinessMembershipRepository;
import com.nempeth.korven.persistence.repository.BusinessRepository;
import com.nempeth.korven.persistence.repository.CategoryRepository;
import com.nempeth.korven.persistence.repository.GoalCategoryTargetRepository;
import com.nempeth.korven.persistence.repository.UserRepository;
import com.nempeth.korven.rest.dto.CategoryResponse;
import com.nempeth.korven.rest.dto.CreateCategoryRequest;
import com.nempeth.korven.rest.dto.UpdateCategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService Tests")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GoalCategoryTargetRepository goalCategoryTargetRepository;

    @InjectMocks
    private CategoryService categoryService;

    private String userEmail;
    private UUID userId;
    private UUID businessId;
    private UUID categoryId;
    private User testUser;
    private Business testBusiness;
    private Category customCategory;
    private Category staticCategory;
    private BusinessMembership testMembership;

    @BeforeEach
    void setUp() {
        userEmail = "test@example.com";
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        testUser = User.builder()
                .id(userId)
                .email(userEmail)
                .name("Test")
                .lastName("User")
                .passwordHash("hashedpassword")
                .build();

        testBusiness = Business.builder()
                .id(businessId)
                .name("Test Business")
                .joinCode("TEST123")
                .build();

        customCategory = Category.builder()
                .id(categoryId)
                .business(testBusiness)
                .name("Custom Category")
                .type(CategoryType.CUSTOM)
                .displayName("Categoría Personalizada")
                .icon("🔧")
                .build();

        staticCategory = Category.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Static Category")
                .type(CategoryType.STATIC)
                .displayName("Categoría Estática")
                .icon("📦")
                .build();

        testMembership = BusinessMembership.builder()
                .user(testUser)
                .business(testBusiness)
                .status(MembershipStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should get categories by business successfully")
    void shouldGetCategoriesByBusinessSuccessfully() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findByBusinessId(businessId))
                .thenReturn(List.of(customCategory, staticCategory));

        List<CategoryResponse> responses = categoryService.getCategoriesByBusiness(userEmail, businessId);

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(CategoryResponse::name)
                .containsExactlyInAnyOrder("Custom Category", "Static Category");
        verify(categoryRepository).findByBusinessId(businessId);
    }

    @Test
    @DisplayName("Should get custom categories by business successfully")
    void shouldGetCustomCategoriesByBusinessSuccessfully() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findByBusinessIdAndType(businessId, CategoryType.CUSTOM))
                .thenReturn(List.of(customCategory));

        List<CategoryResponse> responses = categoryService.getCustomCategoriesByBusiness(userEmail, businessId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Custom Category");
        assertThat(responses.get(0).type()).isEqualTo(CategoryType.CUSTOM);
        verify(categoryRepository).findByBusinessIdAndType(businessId, CategoryType.CUSTOM);
    }

    @Test
    @DisplayName("Should create custom category successfully")
    void shouldCreateCustomCategorySuccessfully() {
        CreateCategoryRequest request = new CreateCategoryRequest(
                "New Category",
                "Nueva Categoría",
                "🎯"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name()))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(customCategory);

        CategoryResponse response = categoryService.createCustomCategory(userEmail, businessId, request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(categoryId);
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Should throw exception when creating category with existing name")
    void shouldThrowExceptionWhenCreatingCategoryWithExistingName() {
        CreateCategoryRequest request = new CreateCategoryRequest(
                "Custom Category",
                "Categoría Personalizada",
                "🔧"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name()))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCustomCategory(userEmail, businessId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe una categoría con ese nombre");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when user has no access to business")
    void shouldThrowExceptionWhenUserHasNoAccessToBusiness() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoriesByBusiness(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No tienes acceso a este negocio");
    }

    @Test
    @DisplayName("Should throw exception when membership is not active")
    void shouldThrowExceptionWhenMembershipIsNotActive() {
        testMembership.setStatus(MembershipStatus.PENDING);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));

        assertThatThrownBy(() -> categoryService.getCategoriesByBusiness(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tu membresía en este negocio no está activa");
    }

    @Test
    @DisplayName("Should delete custom category successfully")
    void shouldDeleteCustomCategorySuccessfully() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));

        categoryService.deleteCustomCategory(userEmail, businessId, categoryId);

        verify(categoryRepository).delete(customCategory);
    }

    @Test
    @DisplayName("Should throw exception when deleting category from different business")
    void shouldThrowExceptionWhenDeletingCategoryFromDifferentBusiness() {
        Business differentBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Different Business")
                .joinCode("DIFF123")
                .build();
        customCategory.setBusiness(differentBusiness);

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));

        assertThatThrownBy(() -> categoryService.deleteCustomCategory(userEmail, businessId, categoryId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La categoría no pertenece a este negocio");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should throw exception when deleting static category")
    void shouldThrowExceptionWhenDeletingStaticCategory() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(staticCategory.getId())).thenReturn(Optional.of(staticCategory));

        assertThatThrownBy(() -> categoryService.deleteCustomCategory(userEmail, businessId, staticCategory.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se puede eliminar una categoría estática");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should update custom category successfully")
    void shouldUpdateCustomCategorySuccessfully() {
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Updated Category",
                "Categoría Actualizada",
                "⚡"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));
        when(categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name()))
                .thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(customCategory);

        CategoryResponse response = categoryService.updateCustomCategory(userEmail, businessId, categoryId, request);

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(Category.class));
        verify(goalCategoryTargetRepository).updateCategoryNameByCategoryId(categoryId, request.name());
    }

    @Test
    @DisplayName("Should throw exception when updating static category")
    void shouldThrowExceptionWhenUpdatingStaticCategory() {
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Updated Category",
                "Categoría Actualizada",
                "⚡"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(staticCategory.getId())).thenReturn(Optional.of(staticCategory));

        assertThatThrownBy(() -> categoryService.updateCustomCategory(userEmail, businessId, staticCategory.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No se puede modificar una categoría estática");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when updating with existing name")
    void shouldThrowExceptionWhenUpdatingWithExistingName() {
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "Existing Category",
                "Categoría Existente",
                "⚡"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));
        when(categoryRepository.existsByBusinessIdAndNameIgnoreCase(businessId, request.name()))
                .thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCustomCategory(userEmail, businessId, categoryId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe una categoría con ese nombre");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update category with same name (case insensitive)")
    void shouldUpdateCategoryWithSameName() {
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                "CUSTOM CATEGORY", // Same name, different case
                "Categoría Actualizada",
                "⚡"
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(customCategory);

        CategoryResponse response = categoryService.updateCustomCategory(userEmail, businessId, categoryId, request);

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(Category.class));
        verify(goalCategoryTargetRepository).updateCategoryNameByCategoryId(categoryId, request.name());
    }

    @Test
    @DisplayName("Should update category with null fields keeping original values")
    void shouldUpdateCategoryWithNullFieldsKeepingOriginalValues() {
        UpdateCategoryRequest request = new UpdateCategoryRequest(
                null, // Keep original name
                "Nuevo Display",
                null  // Keep original icon
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(customCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(customCategory);

        CategoryResponse response = categoryService.updateCustomCategory(userEmail, businessId, categoryId, request);

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(Category.class));
        verify(goalCategoryTargetRepository, never()).updateCategoryNameByCategoryId(any(), any());
    }

    @Test
    @DisplayName("Should throw exception when category not found")
    void shouldThrowExceptionWhenCategoryNotFound() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCustomCategory(userEmail, businessId, categoryId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Categoría no encontrada");
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoriesByBusiness(userEmail, businessId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    @DisplayName("Should return empty list when business has no categories")
    void shouldReturnEmptyListWhenBusinessHasNoCategories() {
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(categoryRepository.findByBusinessId(businessId)).thenReturn(List.of());

        List<CategoryResponse> responses = categoryService.getCategoriesByBusiness(userEmail, businessId);

        assertThat(responses).isEmpty();
    }
}
