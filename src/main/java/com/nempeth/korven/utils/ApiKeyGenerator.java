package com.nempeth.korven.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to generate API keys for external services.
 * Uncomment @Component to run on application startup and generate a new API key.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyGenerator implements CommandLineRunner {

    private final JwtUtils jwtUtils;

    @Override
    public void run(String... args) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "API_KEY");
        claims.put("description", "API Key for external products access");
        
        String apiKey = jwtUtils.generatePermanentApiKey("external-products-api", claims);
        
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERMANENT API KEY GENERATED");
        System.out.println("=".repeat(80));
        System.out.println("API Key: " + apiKey);
        System.out.println("\nUsage:");
        System.out.println("Add this header to your requests:");
        System.out.println("Authorization: Bearer " + apiKey);
        System.out.println("=".repeat(80) + "\n");
    }
}
