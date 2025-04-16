package com.bacos.mokengeli.biloko.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsGlobalConfiguration {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // Autoriser les demandes en provenance de http://localhost:3000
        corsConfig.addAllowedOrigin("http://localhost:3000");
        // Autoriser toutes les méthodes (GET, POST, PUT, DELETE, OPTIONS, ...)
        corsConfig.addAllowedMethod("*");
        // Autoriser tous les headers ou bien ceux dont vous avez besoin
        corsConfig.addAllowedHeader("*");
        // Si vous devez gérer les cookies/credentials
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Appliquer la configuration sur toutes les routes
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }


}
