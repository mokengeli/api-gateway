package com.bacos.mokengeli.biloko.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Configuration WebClient utilisant la découverte Eureka
 * Mis à jour pour utiliser le HttpClient optimisé pour WebSocket
 */
@Slf4j
@Configuration
public class AuthWebClientConfig {

    private final HttpClient gatewayHttpClient;

    @Autowired(required = false)
    public AuthWebClientConfig(HttpClient gatewayHttpClient) {
        this.gatewayHttpClient = gatewayHttpClient;
    }

    /**
     * WebClient avec load balancing pour les appels aux microservices
     */
    @Bean
    @LoadBalanced
    public WebClient webClient(WebClient.Builder builder) {
        log.info("🌐 Creating load-balanced WebClient");
        return builder.build();
    }

    /**
     * WebClient.Builder configuré avec notre HttpClient optimisé
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        WebClient.Builder builder = WebClient.builder();

        // Utiliser le HttpClient optimisé s'il est disponible
        if (gatewayHttpClient != null) {
            builder.clientConnector(new ReactorClientHttpConnector(gatewayHttpClient));
            log.info("✅ WebClient.Builder configured with optimized HttpClient for WebSocket");
        } else {
            log.info("ℹ️ WebClient.Builder using default HttpClient");
        }

        return builder;
    }
}