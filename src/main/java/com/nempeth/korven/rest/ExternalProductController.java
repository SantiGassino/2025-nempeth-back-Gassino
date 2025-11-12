package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.ExternalProductResponse;
import com.nempeth.korven.service.ExternalProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/external/products")
@RequiredArgsConstructor
public class ExternalProductController {
    private final ExternalProductService externalProductService;

    @GetMapping
    public ResponseEntity<List<ExternalProductResponse>> getAllProducts() {
        List<ExternalProductResponse> products = externalProductService.getAllProducts();
        return ResponseEntity.ok(products);
    }
}
