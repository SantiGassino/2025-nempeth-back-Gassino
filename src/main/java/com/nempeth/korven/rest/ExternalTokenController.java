package com.nempeth.korven.rest;

import com.nempeth.korven.rest.dto.ExternalTokenRequest;
import com.nempeth.korven.rest.dto.ExternalTokenResponse;
import com.nempeth.korven.service.ExternalTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/external/token")
@RequiredArgsConstructor
public class ExternalTokenController {
    
    private final ExternalTokenService externalTokenService;

    @PostMapping
    public ResponseEntity<ExternalTokenResponse> generateToken(@Valid @RequestBody ExternalTokenRequest request) {
        ExternalTokenResponse response = externalTokenService.generateToken(request);
        return ResponseEntity.ok(response);
    }
}
