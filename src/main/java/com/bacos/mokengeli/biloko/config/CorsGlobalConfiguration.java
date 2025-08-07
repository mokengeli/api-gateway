
// =============================================================================
// 2. MISE À JOUR: CorsGlobalConfiguration.java
// =============================================================================

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
import java.util.stream.Collectors;

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
        log.info("CORS allowed origins: {}", webOrigins);

        // Patterns pour applications mobiles EXPO
        List<String> mobilePatternsList = Arrays.stream(mobilePatterns.split(","))
                .map(String::trim)
                .collect(Collectors.toList());
        corsConfig.setAllowedOriginPatterns(mobilePatternsList);
        log.info("CORS mobile patterns: {}", mobilePatternsList);


        // Méthodes HTTP autorisées
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Headers autorisés - CRITIQUE pour mobile
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "X-Requested-With",
                "X-Client-Type",      // Nouveau header pour identifier le client
                "X-Client-Platform",  // Nouveau header pour identifier la plateforme
                "Cache-Control",
                "Pragma",
                "Expires",
                "User-Agent"
        ));

        // Headers exposés aux clients - IMPORTANT pour mobile
        corsConfig.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin",
                "Access-Control-Allow-Credentials",
                "Set-Cookie",
                "Authorization",
                "Content-Type",
                "Content-Length",
                "Content-Disposition",
                "X-Total-Count"  // Utile pour la pagination
        ));

        // Gestion des credentials
        // Pour mobile: false (Bearer token)
        // Pour web: true (cookies)
        corsConfig.setAllowCredentials(true);

        // Cache preflight requests - Plus court pour mobile
        corsConfig.setMaxAge(isDevelopmentMode() ? 300L : 1800L); // 5min dev, 30min prod

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        log.info("CORS configuration initialized for mobile and web clients");
        return new CorsWebFilter(source);
    }

    private boolean isDevelopmentMode() {
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.asList(activeProfiles).contains("dev") ||
                activeProfiles.length == 0 ||
                System.getProperty("spring.profiles.active", "").contains("dev");
    }
}
