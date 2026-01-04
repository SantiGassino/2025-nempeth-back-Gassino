package com.nempeth.korven.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalTokenCacheService {
    
    private final CacheManager cacheManager;
    private static final String CACHE_NAME = "externalTokens";

    /**
     * Almacena el token activo para un cliente
     * @param clientName Nombre del cliente
     * @param token Token JWT generado
     */
    public void storeToken(String clientName, String token) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(clientName, token);
            log.debug("Token almacenado en caché para cliente: {}", clientName);
        }
    }

    /**
     * Obtiene el token activo de un cliente
     * @param clientName Nombre del cliente
     * @return El token activo o null si no existe
     */
    public String getActiveToken(String clientName) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(clientName);
            if (wrapper != null) {
                return (String) wrapper.get();
            }
        }
        return null;
    }

    /**
     * Verifica si un token es el token activo para un cliente
     * @param clientName Nombre del cliente
     * @param token Token a verificar
     * @return true si es el token activo, false en caso contrario
     */
    public boolean isActiveToken(String clientName, String token) {
        String activeToken = getActiveToken(clientName);
        if (activeToken == null) {
            // Si no hay token en caché, aceptamos cualquier token válido
            // Esto permite que tokens antiguos sigan funcionando tras restart del servidor
            return true;
        }
        return activeToken.equals(token);
    }

    /**
     * Invalida el token de un cliente
     * @param clientName Nombre del cliente
     */
    public void invalidateToken(String clientName) {
        Cache cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.evict(clientName);
            log.info("Token invalidado para cliente: {}", clientName);
        }
    }
}
