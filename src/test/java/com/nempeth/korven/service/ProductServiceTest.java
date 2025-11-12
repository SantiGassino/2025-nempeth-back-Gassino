package com.nempeth.korven.service;

import com.nempeth.korven.constants.CategoryType;
import com.nempeth.korven.constants.MembershipStatus;
import com.nempeth.korven.persistence.entity.*;
import com.nempeth.korven.persistence.repository.*;
import com.nempeth.korven.rest.dto.ProductResponse;
import com.nempeth.korven.rest.dto.ProductUpsertRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Tests")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private BusinessMembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductService productService;

    private String userEmail;
    private UUID userId;
    private UUID businessId;
    private UUID categoryId;
    private UUID productId;
    private User testUser;
    private Business testBusiness;
    private Category testCategory;
    private Product testProduct;
    private BusinessMembership testMembership;
    private ProductUpsertRequest validRequest;

    @BeforeEach
    void setUp() {
        userEmail = "test@example.com";
        userId = UUID.randomUUID();
        businessId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        productId = UUID.randomUUID();

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

        testCategory = Category.builder()
                .id(categoryId)
                .business(testBusiness)
                .name("Test Category")
                .type(CategoryType.CUSTOM)
                .displayName("Test Display")
                .icon("🍺")
                .build();

        testProduct = Product.builder()
                .id(productId)
                .business(testBusiness)
                .category(testCategory)
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("10.50"))
                .cost(new BigDecimal("5.00"))
                .build();

        testMembership = BusinessMembership.builder()
                .user(testUser)
                .business(testBusiness)
                .status(MembershipStatus.ACTIVE)
                .build();

        validRequest = new ProductUpsertRequest(
                "New Product",
                "New Description",
                new BigDecimal("15.00"),
                new BigDecimal("7.50"),
                categoryId
        );
    }

    @Test
    @DisplayName("Should create product successfully")
    void shouldCreateProductSuccessfully() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsByBusinessIdAndNameIgnoreCase(businessId, validRequest.name()))
                .thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When
        UUID createdId = productService.create(userEmail, businessId, validRequest);

        // Then
        assertThat(createdId).isNotNull();
        assertThat(createdId).isEqualTo(productId);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw exception when user has no access to business")
    void shouldThrowExceptionWhenUserHasNoAccessToBusiness() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No tienes acceso a este negocio");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when membership is not active")
    void shouldThrowExceptionWhenMembershipIsNotActive() {
        // Given
        testMembership.setStatus(MembershipStatus.PENDING);
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tu membresía en este negocio no está activa");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when category does not exist")
    void shouldThrowExceptionWhenCategoryDoesNotExist() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Categoría no encontrada");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when category belongs to different business")
    void shouldThrowExceptionWhenCategoryBelongsToDifferentBusiness() {
        // Given
        Business differentBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Different Business")
                .joinCode("DIFF123")
                .build();
        testCategory.setBusiness(differentBusiness);

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La categoría no pertenece a este negocio");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when product name already exists in business")
    void shouldThrowExceptionWhenProductNameAlreadyExists() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsByBusinessIdAndNameIgnoreCase(businessId, validRequest.name()))
                .thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe un producto con ese nombre en este negocio");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should list products by business")
    void shouldListProductsByBusiness() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        List<Product> products = List.of(testProduct);
        when(productRepository.findByBusinessId(businessId)).thenReturn(products);

        // When
        List<ProductResponse> responses = productService.listByBusiness(userEmail, businessId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(productId);
        assertThat(responses.get(0).name()).isEqualTo("Test Product");
        verify(productRepository).findByBusinessId(businessId);
    }

    @Test
    @DisplayName("Should list products by business and category")
    void shouldListProductsByBusinessAndCategory() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        List<Product> products = List.of(testProduct);
        when(productRepository.findByBusinessIdAndCategoryId(businessId, categoryId))
                .thenReturn(products);

        // When
        List<ProductResponse> responses = productService.listByBusinessAndCategory(userEmail, businessId, categoryId);

        // Then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(productId);
        assertThat(responses.get(0).category().id()).isEqualTo(categoryId);
        verify(productRepository).findByBusinessIdAndCategoryId(businessId, categoryId);
    }

    @Test
    @DisplayName("Should update product successfully")
    void shouldUpdateProductSuccessfully() {
        // Given
        ProductUpsertRequest updateRequest = new ProductUpsertRequest(
                "Updated Product",
                "Updated Description",
                new BigDecimal("20.00"),
                new BigDecimal("10.00"),
                categoryId
        );

        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When
        productService.update(userEmail, businessId, productId, updateRequest);

        // Then
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Should throw exception when updating non-existent product")
    void shouldThrowExceptionWhenUpdatingNonExistentProduct() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.update(userEmail, businessId, productId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Producto no encontrado en este negocio");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete product successfully")
    void shouldDeleteProductSuccessfully() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.of(testProduct));

        // When
        productService.delete(userEmail, businessId, productId);

        // Then
        verify(productRepository).delete(testProduct);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-existent product")
    void shouldThrowExceptionWhenDeletingNonExistentProduct() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(productRepository.findByIdAndBusinessId(productId, businessId))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.delete(userEmail, businessId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Producto no encontrado en este negocio");

        verify(productRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should validate user business access successfully")
    void shouldValidateUserBusinessAccessSuccessfully() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(testBusiness));
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(testCategory));
        when(productRepository.existsByBusinessIdAndNameIgnoreCase(businessId, validRequest.name()))
                .thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(testProduct);

        // When & Then - should not throw exception
        productService.create(userEmail, businessId, validRequest);
    }

    @Test
    @DisplayName("Should throw exception when user not found during validation")
    void shouldThrowExceptionWhenUserNotFoundDuringValidation() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> productService.create(userEmail, businessId, validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Usuario no encontrado");

        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return empty list when business has no products")
    void shouldReturnEmptyListWhenBusinessHasNoProducts() {
        // Given
        when(userRepository.findByEmailIgnoreCase(userEmail)).thenReturn(Optional.of(testUser));
        when(membershipRepository.findByBusinessIdAndUserId(businessId, userId))
                .thenReturn(Optional.of(testMembership));
        when(productRepository.findByBusinessId(businessId)).thenReturn(List.of());

        // When
        List<ProductResponse> responses = productService.listByBusiness(userEmail, businessId);

        // Then
        assertThat(responses).isEmpty();
        verify(productRepository).findByBusinessId(businessId);
    }
}
