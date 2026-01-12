package com.nempeth.korven.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTokenResponse {
    
    private String token;
    private String expiresAt; // Fecha de expiración en formato ISO 8601
    private String message;
}
