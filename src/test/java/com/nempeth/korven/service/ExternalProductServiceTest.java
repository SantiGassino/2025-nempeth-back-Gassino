package com.nempeth.korven.service;

import com.nempeth.korven.persistence.entity.Business;
import com.nempeth.korven.persistence.entity.Category;
import com.nempeth.korven.persistence.entity.Product;
import com.nempeth.korven.persistence.repository.ProductRepository;
import com.nempeth.korven.rest.dto.ExternalProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalProductService Tests")
class ExternalProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ExternalProductService externalProductService;

    private Business testBusiness;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        testBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name("Test Business")
                .joinCode("TEST123")
                .build();

        testCategory = Category.builder()
                .id(UUID.randomUUID())
                .name("Test Category")
                .build();
    }

    @Test
    @DisplayName("Should return all distinct product names")
    void shouldReturnAllDistinctProductNames() {
        // Given
        List<Product> products = List.of(
                createProduct("Laptop"),
                createProduct("Mouse"),
                createProduct("Keyboard")
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder("Laptop", "Mouse", "Keyboard");
        
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("Should return distinct product names when duplicates exist")
    void shouldReturnDistinctProductNamesWhenDuplicatesExist() {
        // Given
        List<Product> products = List.of(
                createProduct("Laptop"),
                createProduct("Mouse"),
                createProduct("Laptop"),  // Duplicate
                createProduct("Keyboard"),
                createProduct("Mouse"),   // Duplicate
                createProduct("Laptop")   // Duplicate
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder("Laptop", "Mouse", "Keyboard");
        
        // Verify no duplicates
        List<String> names = result.stream()
                .map(ExternalProductResponse::getName)
                .toList();
        assertThat(names).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Should return empty list when no products exist")
    void shouldReturnEmptyListWhenNoProductsExist() {
        // Given
        when(productRepository.findAll()).thenReturn(List.of());

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).isEmpty();
        verify(productRepository).findAll();
    }

    @Test
    @DisplayName("Should handle single product")
    void shouldHandleSingleProduct() {
        // Given
        List<Product> products = List.of(createProduct("Single Product"));

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Single Product");
    }

    @Test
    @DisplayName("Should handle products with same name but different properties")
    void shouldHandleProductsWithSameNameButDifferentProperties() {
        // Given
        Product product1 = createProduct("Laptop");
        product1.setPrice(new BigDecimal("1000.00"));
        product1.setCost(new BigDecimal("700.00"));

        Product product2 = createProduct("Laptop");
        product2.setPrice(new BigDecimal("1500.00"));
        product2.setCost(new BigDecimal("1000.00"));

        List<Product> products = List.of(product1, product2);

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        // Should return only one "Laptop" despite different prices
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Laptop");
    }

    @Test
    @DisplayName("Should handle products from different businesses")
    void shouldHandleProductsFromDifferentBusinesses() {
        // Given
        Business business1 = Business.builder()
                .id(UUID.randomUUID())
                .name("Business 1")
                .joinCode("BUS1")
                .build();

        Business business2 = Business.builder()
                .id(UUID.randomUUID())
                .name("Business 2")
                .joinCode("BUS2")
                .build();

        Product product1 = createProduct("Laptop");
        product1.setBusiness(business1);

        Product product2 = createProduct("Laptop");
        product2.setBusiness(business2);

        Product product3 = createProduct("Mouse");
        product3.setBusiness(business1);

        List<Product> products = List.of(product1, product2, product3);

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        // Should return distinct names regardless of business
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder("Laptop", "Mouse");
    }

    @Test
    @DisplayName("Should handle products with different categories")
    void shouldHandleProductsWithDifferentCategories() {
        // Given
        Category category1 = Category.builder()
                .id(UUID.randomUUID())
                .name("Electronics")
                .build();

        Category category2 = Category.builder()
                .id(UUID.randomUUID())
                .name("Accessories")
                .build();

        Product product1 = createProduct("Cable");
        product1.setCategory(category1);

        Product product2 = createProduct("Cable");
        product2.setCategory(category2);

        List<Product> products = List.of(product1, product2);

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        // Should return one "Cable" despite different categories
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Cable");
    }

    @Test
    @DisplayName("Should handle large number of products")
    void shouldHandleLargeNumberOfProducts() {
        // Given
        List<Product> products = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            products.add(createProduct("Product " + (i % 100))); // 100 unique names, each repeated 10 times
        }

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(100); // Should have 100 distinct names
        assertThat(result).extracting(ExternalProductResponse::getName)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Should preserve product name exactly as stored")
    void shouldPreserveProductNameExactlyAsStored() {
        // Given
        List<Product> products = List.of(
                createProduct("Product With Spaces"),
                createProduct("PRODUCT_WITH_UNDERSCORES"),
                createProduct("product-with-dashes"),
                createProduct("Product123"),
                createProduct("Product!@#")
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(5);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder(
                        "Product With Spaces",
                        "PRODUCT_WITH_UNDERSCORES",
                        "product-with-dashes",
                        "Product123",
                        "Product!@#"
                );
    }

    @Test
    @DisplayName("Should handle products with very long names")
    void shouldHandleProductsWithVeryLongNames() {
        // Given
        String longName = "A".repeat(500);
        List<Product> products = List.of(
                createProduct(longName),
                createProduct("Short Name")
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .contains(longName, "Short Name");
    }

    @Test
    @DisplayName("Should handle products with special characters in names")
    void shouldHandleProductsWithSpecialCharactersInNames() {
        // Given
        List<Product> products = List.of(
                createProduct("Product™"),
                createProduct("Product®"),
                createProduct("Product© 2025"),
                createProduct("Product (New)"),
                createProduct("Product & More")
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(5);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder(
                        "Product™",
                        "Product®",
                        "Product© 2025",
                        "Product (New)",
                        "Product & More"
                );
    }

    @Test
    @DisplayName("Should handle products with Unicode characters in names")
    void shouldHandleProductsWithUnicodeCharactersInNames() {
        // Given
        List<Product> products = List.of(
                createProduct("Café"),
                createProduct("Niño"),
                createProduct("北京"),
                createProduct("Москва"),
                createProduct("🎁 Gift")
        );

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).hasSize(5);
        assertThat(result).extracting(ExternalProductResponse::getName)
                .containsExactlyInAnyOrder("Café", "Niño", "北京", "Москва", "🎁 Gift");
    }

    @Test
    @DisplayName("Should return list of ExternalProductResponse objects")
    void shouldReturnListOfExternalProductResponseObjects() {
        // Given
        List<Product> products = List.of(createProduct("Test Product"));

        when(productRepository.findAll()).thenReturn(products);

        // When
        List<ExternalProductResponse> result = externalProductService.getAllProducts();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).allMatch(response -> response instanceof ExternalProductResponse);
        assertThat(result.get(0)).isInstanceOf(ExternalProductResponse.class);
        assertThat(result.get(0).getName()).isEqualTo("Test Product");
    }

    /**
     * Helper method to create a test Product
     */
    private Product createProduct(String name) {
        return Product.builder()
                .id(UUID.randomUUID())
                .business(testBusiness)
                .category(testCategory)
                .name(name)
                .description("Test description for " + name)
                .price(new BigDecimal("100.00"))
                .cost(new BigDecimal("60.00"))
                .build();
    }
}
