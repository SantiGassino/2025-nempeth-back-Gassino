package com.nempeth.korven.security;

import com.nempeth.korven.service.ExternalTokenCacheService;
import com.nempeth.korven.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final ExternalTokenCacheService tokenCacheService;
    
    @Value("${app.api.key.enabled:true}")
    private boolean apiKeyEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        // Only apply to /external/products endpoint
        String path = request.getRequestURI();
        if (!path.startsWith("/external/products")) {
            chain.doFilter(request, response);
            return;
        }

        // Skip for OPTIONS requests
        if ("OPTIONS".equals(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        if (!apiKeyEnabled) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"API Key requerida. Incluya el header Authorization con formato: Bearer <token>\"}");
            return;
        }

        String token = header.substring(7);
        
        try {
            Claims claims = jwtUtils.parseToken(token).getBody();
            String apiKeyType = claims.get("type", String.class);
            
            if (!"API_KEY".equals(apiKeyType)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token inválido. Use una API Key válida\"}");
                return;
            }

            // Obtener clientName para trazabilidad
            String clientName = claims.get("clientName", String.class);
            
            // Verificar si el token es el token activo para este cliente
            if (clientName != null && !tokenCacheService.isActiveToken(clientName, token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Token revocado. Este token ya no es válido. Solicite un nuevo token.\"}");
                return;
            }
            
            if (clientName != null) {
                logger.info("Acceso a API externa desde cliente: " + clientName);
            }

            // Create authentication without user
            var authToken = new UsernamePasswordAuthenticationToken(
                    "api-key-user", 
                    null, 
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY"))
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            chain.doFilter(request, response);
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"API Key inválida o expirada\"}");
        }
    }
}
