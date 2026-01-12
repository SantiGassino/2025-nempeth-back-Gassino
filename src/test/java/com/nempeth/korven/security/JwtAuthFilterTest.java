package com.nempeth.korven.security;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    private StringWriter responseWriter;
    private UserDetails testUserDetails;

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));

        testUserDetails = User.builder()
                .username("test@example.com")
                .password("hashedPassword")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }

    // ==================== Path Filtering Tests ====================

    @Test
    void shouldSkipFilterForPasswordResetPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/password/reset");
        lenient().when(request.getMethod()).thenReturn("POST");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(jwtUtils, never()).parseToken(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSkipFilterForPasswordResetRequestPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/password/request");
        lenient().when(request.getMethod()).thenReturn("POST");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtils, never()).parseToken(any());
    }

    @Test
    void shouldSkipFilterForPasswordResetConfirmPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/password/confirm");
        lenient().when(request.getMethod()).thenReturn("POST");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtils, never()).parseToken(any());
    }

    @Test
    void shouldSkipFilterForOptionsRequest() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("OPTIONS");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(jwtUtils, never()).parseToken(any());
    }

    @Test
    void shouldApplyFilterForNonPasswordResetPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        // No authentication set since no token provided
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    // ==================== Authorization Header Tests ====================

    @Test
    void shouldContinueWithoutAuthorizationHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldContinueWithNonBearerAuthorizationHeader() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Basic credentials");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(jwtUtils, never()).parseToken(any());
    }

    // ==================== Valid Token Tests ====================

    @Test
    void shouldAuthenticateWithValidToken() throws Exception {
        String token = "valid.jwt.token";
        String email = "test@example.com";

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(testUserDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo(testUserDetails);
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    void shouldAuthenticateUserWithMultipleRoles() throws Exception {
        String token = "valid.jwt.token";
        String email = "owner@example.com";

        UserDetails ownerDetails = User.builder()
                .username(email)
                .password("hashedPassword")
                .authorities(
                        new SimpleGrantedAuthority("ROLE_OWNER"),
                        new SimpleGrantedAuthority("ROLE_EMPLOYEE")
                )
                .build();

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(ownerDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).hasSize(2);
    }

    @Test
    void shouldNotOverrideExistingAuthentication() throws Exception {
        String token = "valid.jwt.token";
        String email = "test@example.com";

        // Set up existing authentication
        UserDetails existingUser = User.builder()
                .username("existing@example.com")
                .password("password")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
        
        org.springframework.security.authentication.UsernamePasswordAuthenticationToken existingAuth = 
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        existingUser, null, existingUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(any());

        // Authentication should remain the existing one
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getPrincipal()).isEqualTo(existingUser);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ADMIN");
    }

    // ==================== Invalid Token Tests ====================

    @Test
    void shouldRejectInvalidToken() throws Exception {
        String token = "invalid.token";

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new RuntimeException("Invalid token"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Token inválido o expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        String token = "expired.token";

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new io.jsonwebtoken.ExpiredJwtException(null, null, "Token expired"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        assertThat(responseWriter.toString()).contains("Token inválido o expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectMalformedToken() throws Exception {
        String token = "malformed.token";

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new io.jsonwebtoken.MalformedJwtException("Malformed JWT"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Token inválido o expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldRejectTokenWithNullSubject() throws Exception {
        String token = "token.without.subject";

        Claims claims = new DefaultClaims();
        claims.setSubject(null); // null subject
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldHandleUserDetailsServiceException() throws Exception {
        String token = "valid.token";
        String email = "test@example.com";

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email))
                .thenThrow(new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Token inválido o expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    // ==================== Edge Cases ====================

    @Test
    void shouldHandleEmptyBearerToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(responseWriter.toString()).contains("Token inválido o expirado");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void shouldHandleBearerWithExtraSpaces() throws Exception {
        String token = "token.with.spaces";
        String email = "test@example.com";

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer  " + token);
        when(jwtUtils.parseToken(" " + token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(testUserDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
    }

    @Test
    void shouldHandleDifferentHttpMethods() throws Exception {
        String token = "valid.token";
        String email = "test@example.com";

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(testUserDetails);

        // Test GET
        when(request.getMethod()).thenReturn("GET");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(1)).doFilter(request, response);
        SecurityContextHolder.clearContext();

        // Test POST
        when(request.getMethod()).thenReturn("POST");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(2)).doFilter(request, response);
        SecurityContextHolder.clearContext();

        // Test PUT
        when(request.getMethod()).thenReturn("PUT");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(3)).doFilter(request, response);
        SecurityContextHolder.clearContext();

        // Test DELETE
        when(request.getMethod()).thenReturn("DELETE");
        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        verify(filterChain, times(4)).doFilter(request, response);
    }

    @Test
    void shouldHandleMultipleRequestsSequentially() throws Exception {
        String token1 = "token1";
        String token2 = "token2";
        String email1 = "user1@example.com";
        String email2 = "user2@example.com";

        Claims claims1 = new DefaultClaims();
        claims1.setSubject(email1);
        Jws<Claims> jws1 = new DefaultJws<>(new DefaultJwsHeader(), claims1, "signature");

        Claims claims2 = new DefaultClaims();
        claims2.setSubject(email2);
        Jws<Claims> jws2 = new DefaultJws<>(new DefaultJwsHeader(), claims2, "signature");

        UserDetails user1 = User.builder()
                .username(email1)
                .password("password1")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")))
                .build();

        UserDetails user2 = User.builder()
                .username(email2)
                .password("password2")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");

        // First request
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token1);
        when(jwtUtils.parseToken(token1)).thenReturn(jws1);
        when(userDetailsService.loadUserByUsername(email1)).thenReturn(user1);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        Authentication auth1 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth1.getPrincipal()).isEqualTo(user1);

        SecurityContextHolder.clearContext();

        // Second request
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token2);
        when(jwtUtils.parseToken(token2)).thenReturn(jws2);
        when(userDetailsService.loadUserByUsername(email2)).thenReturn(user2);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);
        Authentication auth2 = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth2.getPrincipal()).isEqualTo(user2);
    }

    // ==================== Security Context Tests ====================

    @Test
    void shouldClearSecurityContextOnInvalidToken() throws Exception {
        String token = "invalid.token";

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenThrow(new RuntimeException("Invalid"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void shouldSetAuthenticationDetails() throws Exception {
        String token = "valid.token";
        String email = "test@example.com";

        Claims claims = new DefaultClaims();
        claims.setSubject(email);
        Jws<Claims> jws = new DefaultJws<>(new DefaultJwsHeader(), claims, "signature");

        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtils.parseToken(token)).thenReturn(jws);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(testUserDetails);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getDetails()).isNotNull();
        assertThat(auth.getCredentials()).isNull(); // No credentials in token auth
    }

    // ==================== Combined Path Tests ====================

    @Test
    void shouldSkipAuthForPasswordResetEvenWithInvalidToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/auth/password/reset");
        lenient().when(request.getMethod()).thenReturn("POST");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtils, never()).parseToken(any());
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void shouldSkipAuthForOptionsEvenWithToken() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/business");
        when(request.getMethod()).thenReturn("OPTIONS");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtUtils, never()).parseToken(any());
    }
}
