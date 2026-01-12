package com.nempeth.korven.security;

import com.nempeth.korven.service.ExternalTokenCacheService;
import com.nempeth.korven.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.impl.DefaultClaims;
import io.jsonwebtoken.impl.DefaultJws;
import io.jsonwebtoken.impl.DefaultJwsHeader;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private ExternalTokenCacheService tokenCacheService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ApiKeyAuthFilter apiKeyAuthFilter;

    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
        
        // Set apiKeyEnabled to true by default
        ReflectionTestUtils.setField(apiKeyAuthFilter, "apiKeyEnabled", true);
    }

    // ==================== Path Filtering Tests ====================

    @Test
    void shouldSkipFilterForNonExternalProductsPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldApplyFilterForExternalProductsPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldApplyFilterForExternalProductsSubPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products/search");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ==================== OPTIONS Request Tests ====================

    @Test
    void shouldSkipFilterForOptionsRequest() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("OPTIONS");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // ==================== API Key Enabled/Disabled Tests ====================

    @Test
    void shouldSkipFilterWhenApiKeyDisabled() throws Exception {
        ReflectionTestUtils.setField(apiKeyAuthFilter, "apiKeyEnabled", false);
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    // ==================== Authorization Header Tests ====================

    @Test
    void shouldRejectRequestWithoutAuthorizationHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key requerida");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectRequestWithoutBearerPrefix() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("InvalidToken");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key requerida");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectRequestWithEmptyBearerToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key inválida o expirada");
    }

    // ==================== Token Validation Tests ====================

    @Test
    void shouldAcceptValidApiKeyToken() throws Exception {
        String token = "valid-api-key-token";
        String clientName = "TestClient";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token)).thenReturn(true);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("api-key-user");
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_API_KEY");
    }

    @Test
    void shouldRejectTokenWithWrongType() throws Exception {
        String token = "wrong-type-token";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "USER_TOKEN");
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Token inválido");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectTokenWithoutType() throws Exception {
        String token = "no-type-token";
        
        Claims claims = new DefaultClaims();
        // No type claim
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Token inválido");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectRevokedToken() throws Exception {
        String token = "revoked-token";
        String clientName = "RevokedClient";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token)).thenReturn(false);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Token revocado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldAcceptTokenWithoutClientName() throws Exception {
        String token = "token-without-client";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        // No clientName
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(tokenCacheService, never()).isActiveToken(any(), any());
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
    }

    // ==================== Exception Handling Tests ====================

    @Test
    void shouldRejectInvalidToken() throws Exception {
        String token = "invalid-token";

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new RuntimeException("Invalid token"));

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key inválida o expirada");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldHandleExpiredToken() throws Exception {
        String token = "expired-token";

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"));

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key inválida o expirada");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldHandleMalformedToken() throws Exception {
        String token = "malformed-token";

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed token"));

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("API Key inválida o expirada");
        verify(filterChain, never()).doFilter(request, response);
    }

    // ==================== Security Context Tests ====================

    @Test
    void shouldSetAuthenticationInSecurityContext() throws Exception {
        String token = "valid-token";
        String clientName = "SecureClient";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token)).thenReturn(true);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.isAuthenticated()).isTrue();
        assertThat(auth.getPrincipal()).isEqualTo("api-key-user");
        assertThat(auth.getCredentials()).isNull();
    }

    @Test
    void shouldNotSetAuthenticationOnRejection() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==================== Multiple Requests Tests ====================

    @Test
    void shouldHandleMultipleValidRequests() throws Exception {
        String token1 = "token-1";
        String token2 = "token-2";
        String clientName = "MultiClient";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        // First request
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token1);
        when(jwtUtils.parseToken(token1)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token1)).thenReturn(true);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);
        
        SecurityContextHolder.clearContext();

        // Second request with different token
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token2);
        when(jwtUtils.parseToken(token2)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token2)).thenReturn(true);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(2)).doFilter(request, response);
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleBearerWithExtraSpaces() throws Exception {
        String token = "token-with-spaces";

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer  " + token);
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");
        when(jwtUtils.parseToken(" " + token)).thenReturn(jws);

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldRejectBearerInLowercase() throws Exception {
        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("bearer valid-token");

        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("API Key requerida");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldHandleDifferentHttpMethods() throws Exception {
        String token = "method-token";
        String clientName = "MethodClient";
        
        Claims claims = new DefaultClaims();
        claims.put("type", "API_KEY");
        claims.put("clientName", clientName);
        
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/external/products");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(tokenCacheService.isActiveToken(clientName, token)).thenReturn(true);

        // Test GET
        when(request.getMethod()).thenReturn("GET");
        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);
        
        SecurityContextHolder.clearContext();

        // Test POST
        when(request.getMethod()).thenReturn("POST");
        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(2)).doFilter(request, response);
        
        SecurityContextHolder.clearContext();

        // Test PUT
        when(request.getMethod()).thenReturn("PUT");
        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(3)).doFilter(request, response);
        
        SecurityContextHolder.clearContext();

        // Test DELETE
        when(request.getMethod()).thenReturn("DELETE");
        apiKeyAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(4)).doFilter(request, response);
    }
}
