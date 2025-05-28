package com.bacos.mokengeli.biloko.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsGlobalConfiguration {

    @Value("${security.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${security.cors.mobile-patterns}")
    private String mobilePatterns;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Origines web classiques
        List<String> webOrigins = Arrays.asList(allowedOrigins.split(","));
        corsConfig.setAllowedOrigins(webOrigins);

        // Patterns pour applications mobiles
        List<String> mobilePatternsList = Arrays.asList(mobilePatterns.split(","));
        corsConfig.setAllowedOriginPatterns(mobilePatternsList);

        // En développement, autoriser tous les origins localhost et IP locales
        if (isDevelopmentMode()) {
            corsConfig.addAllowedOriginPattern("http://localhost:*");
            corsConfig.addAllowedOriginPattern("https://localhost:*");
            corsConfig.addAllowedOriginPattern("http://127.0.0.1:*");
            corsConfig.addAllowedOriginPattern("http://10.0.2.2:*"); // Android emulator
            corsConfig.addAllowedOriginPattern("http://192.168.*:*"); // LAN
            corsConfig.addAllowedOriginPattern("https://*.ngrok-free.app");
            corsConfig.addAllowedOriginPattern("https://*.ngrok.app");
            corsConfig.addAllowedOriginPattern("https://*.ngrok.io");
        }

        // Patterns pour Expo
        corsConfig.addAllowedOriginPattern("exp://*");
        corsConfig.addAllowedOriginPattern("exps://*");
        corsConfig.addAllowedOriginPattern("https://expo.dev");
        corsConfig.addAllowedOriginPattern("https://*.expo.dev");
        corsConfig.addAllowedOriginPattern("https://u.expo.dev");

        // Méthodes HTTP autorisées
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"
        ));

        // Headers autorisés
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Origin", "Content-Type", "Accept", "Authorization",
                "Access-Control-Request-Method", "Access-Control-Request-Headers",
                "X-Requested-With", "Cache-Control", "Pragma", "Expires",
                "User-Agent", "X-CSRF-Token", "Cookie"
        ));

        // Headers exposés aux clients
        corsConfig.setExposedHeaders(Arrays.asList(
                "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials",
                "Set-Cookie", "Authorization"
        ));

        // Autoriser les credentials (cookies, headers d'auth)
        corsConfig.setAllowCredentials(true);

        // Cache preflight requests for 1 hour
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }

    private boolean isDevelopmentMode() {
        String activeProfiles = System.getProperty("spring.profiles.active", "");
        return activeProfiles.contains("dev") || activeProfiles.isEmpty();
    }
}