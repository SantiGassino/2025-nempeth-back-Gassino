package com.nempeth.korven.service;

import com.nempeth.korven.persistence.repository.ProductRepository;
import com.nempeth.korven.rest.dto.ExternalProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalProductService {
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<ExternalProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(product -> product.getName())
                .distinct()
                .map(name -> ExternalProductResponse.builder()
                        .name(name)
                        .build())
                .collect(Collectors.toList());
    }
}
