package com.nempeth.korven.persistence.repository;

import com.nempeth.korven.constants.CategoryType;
import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.Category;
import com.nempeth.korven.persistence.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ProductRepository Tests")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Business testBusiness;
    private Category testCategory;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        
        testBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode("TEST123")
                .build();
        entityManager.persist(testBusiness);

        testCategory = Category.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .name("Test Category")
                .type(CategoryType.CUSTOM)
                .displayName("Bebidas")
                .icon("🍺")
                .build();
        entityManager.persist(testCategory);

        testProduct = Product.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .category(testCategory)
                .name("Test Product")
                .description("Test Description")
                .price(new BigDecimal("10.50"))
                .cost(new BigDecimal("5.00"))
                .build();
        entityManager.persist(testProduct);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find products by business ID")
    void shouldFindProductsByBusinessId() {
        List<Product> products = productRepository.findByBusinessId(testBusiness.getId());

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getName()).isEqualTo("Test Product");
        assertThat(products.get(0).getBusiness().getId()).isEqualTo(testBusiness.getId());
    }

    @Test
    @DisplayName("Should return empty list when no products for business")
    void shouldReturnEmptyListWhenNoProductsForBusiness() {
        UUID nonExistentBusinessId = UUID.randomUUID();
        List<Product> products = productRepository.findByBusinessId(nonExistentBusinessId);
        assertThat(products).isEmpty();
    }

    @Test
    @DisplayName("Should find products by business ID and category ID")
    void shouldFindProductsByBusinessIdAndCategoryId() {
        List<Product> products = productRepository.findByBusinessIdAndCategoryId(
                testBusiness.getId(), 
                testCategory.getId()
        );

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getName()).isEqualTo("Test Product");
        assertThat(products.get(0).getCategory().getId()).isEqualTo(testCategory.getId());
    }

    @Test
    @DisplayName("Should return empty list when no products for business and category")
    void shouldReturnEmptyListWhenNoProductsForBusinessAndCategory() {
        UUID nonExistentCategoryId = UUID.randomUUID();
        List<Product> products = productRepository.findByBusinessIdAndCategoryId(
                testBusiness.getId(), 
                nonExistentCategoryId
        );

        assertThat(products).isEmpty();
    }

    @Test
    @DisplayName("Should find product by ID and business ID")
    void shouldFindProductByIdAndBusinessId() {
        Optional<Product> found = productRepository.findByIdAndBusinessId(
                testProduct.getId(), 
                testBusiness.getId()
        );

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Test Product");
        assertThat(found.get().getPrice()).isEqualByComparingTo(new BigDecimal("10.50"));
    }

    @Test
    @DisplayName("Should return empty when product not found for business")
    void shouldReturnEmptyWhenProductNotFoundForBusiness() {
        UUID differentBusinessId = UUID.randomUUID();

        Optional<Product> found = productRepository.findByIdAndBusinessId(
                testProduct.getId(), 
                differentBusinessId
        );
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should check if product exists by business ID and name ignoring case")
    void shouldCheckIfProductExistsByBusinessIdAndNameIgnoreCase() {
        boolean existsExact = productRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "Test Product"
        );
        boolean existsLower = productRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "test product"
        );
        boolean existsUpper = productRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "TEST PRODUCT"
        );

        assertThat(existsExact).isTrue();
        assertThat(existsLower).isTrue();
        assertThat(existsUpper).isTrue();
    }

    @Test
    @DisplayName("Should return false when product name does not exist in business")
    void shouldReturnFalseWhenProductNameDoesNotExistInBusiness() {
        boolean exists = productRepository.existsByBusinessIdAndNameIgnoreCase(
                testBusiness.getId(), 
                "Non Existent Product"
        );
        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("Should save product with all fields")
    void shouldSaveProductWithAllFields() {
        Product newProduct = Product.builder()
                .business(testBusiness)
                .category(testCategory)
                .name("New Product")
                .description("New Description")
                .price(new BigDecimal("15.00"))
                .cost(new BigDecimal("7.50"))
                .build();

        Product saved = productRepository.save(newProduct);
        entityManager.flush();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("New Product");
        assertThat(saved.getDescription()).isEqualTo("New Description");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(saved.getCost()).isEqualByComparingTo(new BigDecimal("7.50"));
    }

    @Test
    @DisplayName("Should update existing product")
    void shouldUpdateExistingProduct() {
        testProduct.setName("Updated Product");
        testProduct.setPrice(new BigDecimal("20.00"));
        productRepository.save(testProduct);
        entityManager.flush();
        entityManager.clear();
        Product found = productRepository.findById(testProduct.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("Updated Product");
        assertThat(found.getPrice()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    @DisplayName("Should delete product")
    void shouldDeleteProduct() {
        productRepository.delete(testProduct);
        entityManager.flush();
        Optional<Product> found = productRepository.findById(testProduct.getId());
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should find multiple products by business")
    void shouldFindMultipleProductsByBusiness() {
        Product product2 = Product.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .category(testCategory)
                .name("Second Product")
                .description("Second Description")
                .price(new BigDecimal("12.00"))
                .cost(new BigDecimal("6.00"))
                .build();
        entityManager.persist(product2);
        entityManager.flush();
        List<Product> products = productRepository.findByBusinessId(testBusiness.getId());

        assertThat(products).hasSize(2);
        assertThat(products).extracting(Product::getName)
                .containsExactlyInAnyOrder("Test Product", "Second Product");
    }
}
