package com.nempeth.korven.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtils {

    private final Key key;
    private final long expirationMs;

    public JwtUtils(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expirationMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates a permanent API key token without expiration date
     * @param identifier Identifier for the API key (e.g., "external-api")
     * @param claims Additional claims for the token
     * @return A JWT token without expiration
     */
    public String generatePermanentApiKey(String identifier, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(identifier)
                .addClaims(claims)
                .setIssuedAt(new Date(now))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generates an API key token with 24 hours expiration
     * @param identifier Identifier for the API key (e.g., "external-api")
     * @param claims Additional claims for the token
     * @return A JWT token that expires in 24 hours
     */
    public String generateExternalApiToken(String identifier, Map<String, Object> claims) {
        long now = System.currentTimeMillis();
        long twentyFourHoursMs = 24 * 60 * 60 * 1000; // 24 horas en milisegundos
        
        return Jwts.builder()
                .setSubject(identifier)
                .addClaims(claims)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + twentyFourHoursMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }
    
    public Date getExpirationFromToken(String token) {
        return parseToken(token).getBody().getExpiration();
    }
}
