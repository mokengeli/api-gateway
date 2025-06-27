package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filter de debug amélioré pour surveiller les requêtes CORS
 * À SUPPRIMER en production
 */
@Slf4j
@Component
public class CorsDebugFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        HttpMethod method = exchange.getRequest().getMethod();
        String origin = headers.getFirst("Origin");
        String userAgent = headers.getFirst("User-Agent");
        String path = exchange.getRequest().getPath().toString();

        // Log TOUTES les requêtes pour le debug
        log.info("=== INCOMING REQUEST ===");
        log.info("Method: {}, Path: {}", method, path);
        log.info("Origin: {}", origin != null ? origin : "NO ORIGIN HEADER");
        log.info("User-Agent: {}", userAgent);

        // Détection des requêtes mobiles
        boolean isMobileRequest = userAgent != null &&
                (userAgent.contains("Expo") ||
                        userAgent.contains("okhttp") ||
                        userAgent.contains("React Native"));

        if (isMobileRequest) {
            log.warn("MOBILE REQUEST DETECTED from User-Agent: {}", userAgent);
        }

        // Log tous les headers pour les requêtes CORS ou mobiles
        if (origin != null || HttpMethod.OPTIONS.equals(method) || isMobileRequest) {
            log.info("=== REQUEST HEADERS ===");
            headers.forEach((key, values) -> {
                log.info("{}: {}", key, values);
            });
        }

        // Log spécifique pour les requêtes OPTIONS (preflight)
        if (HttpMethod.OPTIONS.equals(method)) {
            log.warn("PREFLIGHT REQUEST - Origin: {}, Path: {}", origin, path);
            String requestMethod = headers.getFirst("Access-Control-Request-Method");
            String requestHeaders = headers.getFirst("Access-Control-Request-Headers");
            log.info("Preflight details - Method: {}, Headers: {}", requestMethod, requestHeaders);
        }

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // Log des headers de réponse
            if (origin != null || HttpMethod.OPTIONS.equals(method) || isMobileRequest) {
                HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
                log.info("=== RESPONSE STATUS: {} ===", exchange.getResponse().getStatusCode());

                // Log tous les headers Access-Control
                responseHeaders.forEach((key, values) -> {
                    if (key.toLowerCase().contains("access-control")) {
                        log.info("Response Header - {}: {}", key, values);
                    }
                });

                // Vérification des headers CORS critiques
                if (!responseHeaders.containsKey("Access-Control-Allow-Origin")) {
                    log.error("MISSING Access-Control-Allow-Origin header!");
                }
                if (origin != null && !responseHeaders.getAccessControlAllowOrigin().equals(origin) &&
                        !responseHeaders.getAccessControlAllowOrigin().equals("*")) {
                    log.error("Origin mismatch! Request: {}, Response: {}",
                            origin, responseHeaders.getAccessControlAllowOrigin());
                }
            }
        }));
    }

    @Override
    public int getOrder() {
        return -100; // Très haute priorité pour capturer tout
    }
}