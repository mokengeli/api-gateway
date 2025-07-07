package com.bacos.mokengeli.biloko.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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
        List<String> webOrigins = Arrays.asList(allowedOrigins.split(","));
        corsConfig.setAllowedOrigins(webOrigins);
        log.info("CORS allowed origins: {}", webOrigins);

        // Patterns pour applications mobiles
        List<String> mobilePatternsList = Arrays.asList(mobilePatterns.split(","));
        corsConfig.setAllowedOriginPatterns(mobilePatternsList);
        log.info("CORS mobile patterns: {}", mobilePatternsList);

        // En développement, ajouter automatiquement localhost
        if (isDevelopmentMode()) {
            corsConfig.addAllowedOriginPattern("http://localhost:*");
            corsConfig.addAllowedOriginPattern("https://localhost:*");
            corsConfig.addAllowedOriginPattern("http://127.0.0.1:*");
            log.info("Development mode: added localhost patterns");
        }

        // Méthodes HTTP autorisées
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Headers autorisés - liste complète incluant Authorization
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Requested-With",
                "Cache-Control",
                "Pragma",
                "Expires"
        ));

        // Headers exposés aux clients - IMPORTANT pour mobile
        corsConfig.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Set-Cookie",
                "Authorization",  // Important pour que le client mobile puisse lire ce header
                "Content-Type",
                "Content-Length"
        ));

        // Autoriser les credentials
        // Note: avec credentials=true, on ne peut pas utiliser "*" pour les origines
        corsConfig.setAllowCredentials(true);

        // Cache preflight requests
        corsConfig.setMaxAge(isDevelopmentMode() ? 300L : 3600L); // 5min dev, 1h prod

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        log.info("CORS configuration initialized successfully");
        return new CorsWebFilter(source);
    }

    private boolean isDevelopmentMode() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("dev") ||
                activeProfiles.length == 0 || // Aucun profil = développement
                System.getProperty("spring.profiles.active", "").contains("dev");
    }
}