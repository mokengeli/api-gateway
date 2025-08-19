package com.bacos.mokengeli.biloko.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Configuration CORS globale adaptée pour WebSocket et HTTP
 */
@Slf4j
@Configuration
public class CorsGlobalConfiguration {

    @Value("${security.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${security.cors.mobile-patterns}")
    private String mobilePatterns;

    private final Environment environment;

    public CorsGlobalConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Origines web classiques
        List<String> webOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        corsConfig.setAllowedOrigins(webOrigins);
        log.info("✅ CORS allowed origins: {}", webOrigins);

        // Patterns pour applications mobiles EXPO
        List<String> mobilePatternsList = Arrays.stream(mobilePatterns.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        corsConfig.setAllowedOriginPatterns(mobilePatternsList);
        log.info("✅ CORS mobile patterns: {}", mobilePatternsList);

        // Méthodes HTTP autorisées (incluant celles pour WebSocket handshake)
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD", "CONNECT"
        ));

        // Headers autorisés - CRITIQUE pour mobile et WebSocket
        corsConfig.setAllowedHeaders(Arrays.asList(
                // Headers standard
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Requested-With",

                // Headers custom pour identification
                "X-Client-Type",
                "X-Client-Platform",
                "X-Tenant-Code",

                // Headers de cache
                "Cache-Control",
                "Pragma",
                "Expires",

                // Headers WebSocket
                "Upgrade",
                "Connection",
                "Sec-WebSocket-Key",
                "Sec-WebSocket-Version",
                "Sec-WebSocket-Protocol",
                "Sec-WebSocket-Extensions",
                "Sec-WebSocket-Accept",

                // User agent
                "User-Agent",

                // Headers STOMP (si passés dans HTTP)
                "heart-beat",
                "accept-version"
        ));

        // Headers exposés aux clients - IMPORTANT pour mobile et WebSocket
        corsConfig.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Set-Cookie",
                "Authorization",
                "Content-Type",
                "Content-Length",
                "Content-Disposition",
                "X-Total-Count",

                // Headers WebSocket exposés
                "Upgrade",
                "Connection",
                "Sec-WebSocket-Accept",
                "Sec-WebSocket-Protocol"
        ));

        // Gestion des credentials
        // true pour permettre cookies ET WebSocket avec auth
        corsConfig.setAllowCredentials(true);

        // Cache preflight requests
        // Plus court en dev, plus long en prod
        corsConfig.setMaxAge(isDevelopmentMode() ? 300L : 3600L); // 5min dev, 1h prod

        // Configuration spécifique pour WebSocket paths
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Configuration globale
        source.registerCorsConfiguration("/**", corsConfig);

        // Configuration spécifique pour WebSocket (plus permissive si nécessaire)
        CorsConfiguration wsConfig = new CorsConfiguration(corsConfig);
        wsConfig.setAllowedMethods(Arrays.asList("*")); // Toutes les méthodes pour WS
        source.registerCorsConfiguration("/api/order/ws/**", wsConfig);
        source.registerCorsConfiguration("/api/order/ws/websocket/**", wsConfig);

        log.info("🌐 CORS configuration initialized for HTTP and WebSocket");
        log.info("📱 Mobile support enabled with patterns: {}", mobilePatternsList);
        log.info("🔌 WebSocket CORS configured for /api/order/ws/** paths");

        return new CorsWebFilter(source);
    }

    private boolean isDevelopmentMode() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("dev") ||
                activeProfiles.length == 0 ||
                System.getProperty("spring.profiles.active", "").contains("dev");
    }
}