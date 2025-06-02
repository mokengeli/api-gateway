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
 * Filter de debug pour surveiller les requêtes CORS
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

        // Log des requêtes CORS et mobiles
        if (origin != null || HttpMethod.OPTIONS.equals(method)) {
            log.info("CORS Request - Method: {}, Origin: {}, Path: {}, User-Agent: {}",
                    method, origin, exchange.getRequest().getPath(), userAgent);

            // Log des headers de requête importants
            headers.forEach((key, values) -> {
                if (key.toLowerCase().contains("access-control") ||
                        key.toLowerCase().contains("origin") ||
                        key.toLowerCase().contains("cors")) {
                    log.debug("Request Header - {}: {}", key, values);
                }
            });
        }

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // Log des headers de réponse CORS
            if (origin != null || HttpMethod.OPTIONS.equals(method)) {
                HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
                log.info("CORS Response - Status: {}, Headers: {}",
                        exchange.getResponse().getStatusCode(),
                        responseHeaders.entrySet().stream()
                                .filter(entry -> entry.getKey().toLowerCase().contains("access-control"))
                                .collect(java.util.stream.Collectors.toMap(
                                        java.util.Map.Entry::getKey,
                                        java.util.Map.Entry::getValue))
                );
            }
        }));
    }

    @Override
    public int getOrder() {
        return -10; // Avant tous les autres filters
    }
}