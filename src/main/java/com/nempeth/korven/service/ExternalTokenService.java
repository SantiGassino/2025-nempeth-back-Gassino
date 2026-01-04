package com.nempeth.korven.service;

import com.nempeth.korven.rest.dto.ExternalTokenRequest;
import com.nempeth.korven.rest.dto.ExternalTokenResponse;
import com.nempeth.korven.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalTokenService {
    
    private final JwtUtils jwtUtils;
    private final ExternalTokenCacheService tokenCacheService;

    public ExternalTokenResponse generateToken(ExternalTokenRequest request) {
        String clientName = request.getClientName();
        
        // Verificar si existe un token anterior
        String oldToken = tokenCacheService.getActiveToken(clientName);
        if (oldToken != null) {
            log.info("Invalidando token anterior para cliente: {}", clientName);
        }
        
        // Log para trazabilidad
        log.info("Generando token de API externa para cliente: {}", clientName);
        
        // Crear claims con información del cliente
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        claims.put("timestamp", System.currentTimeMillis());
        
        // Generar token con expiración de 24 horas
        String token = jwtUtils.generateExternalApiToken("external-api", claims);
        
        // Almacenar el nuevo token (invalida automáticamente el anterior)
        tokenCacheService.storeToken(clientName, token);
        
        // Obtener fecha de expiración y formatearla
        long expiresAtTimestamp = System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        String expiresAt = DateTimeFormatter.ISO_INSTANT
                .format(Instant.ofEpochMilli(expiresAtTimestamp));
        
        log.info("Token generado exitosamente para cliente: {} (expira en 24 horas: {})", clientName, expiresAt);
        
        return ExternalTokenResponse.builder()
                .token(token)
                .expiresAt(expiresAt)
                .message("Token generado exitosamente para " + clientName + ". El token expira en 24 horas.")
                .build();
    }
}
