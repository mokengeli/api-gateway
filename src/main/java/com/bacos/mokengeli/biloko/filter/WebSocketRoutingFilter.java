package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

/**
 * Filtre global pour gérer le routage et l'authentification WebSocket
 * Ce filtre s'assure que les connexions WebSocket passent correctement
 */
@Slf4j
@Component
public class WebSocketRoutingFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String UPGRADE_HEADER = "Upgrade";
    private static final String CONNECTION_HEADER = "Connection";
    private static final String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    private static final String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Vérifier si c'est une requête WebSocket
        if (!isWebSocketRequest(request)) {
            return chain.filter(exchange);
        }

        String path = request.getPath().toString();
        log.info("🔌 WebSocket request detected for path: {}", path);

        // Logger les headers WebSocket pour debug
        logWebSocketHeaders(request);

        // Pour les WebSockets, on laisse Spring Cloud Gateway gérer le proxying
        // On ne modifie PAS l'URI car cela interfère avec le routage

        // Vérifier que le token est présent (soit en query param, soit en header)
        boolean hasToken = hasAuthenticationToken(request);
        if (!hasToken) {
            log.warn("⚠️ No authentication token found for WebSocket request");
        }

        // Passer la requête sans modification
        // Spring Cloud Gateway gère automatiquement le proxy WebSocket
        return chain.filter(exchange);
    }

    /**
     * Vérifie si la requête est une tentative de connexion WebSocket
     */
    private boolean isWebSocketRequest(ServerHttpRequest request) {
        String upgrade = request.getHeaders().getFirst(UPGRADE_HEADER);
        String connection = request.getHeaders().getFirst(CONNECTION_HEADER);

        return "websocket".equalsIgnoreCase(upgrade) &&
                connection != null && connection.toLowerCase().contains("upgrade");
    }

    /**
     * Vérifie si un token d'authentification est présent
     */
    private boolean hasAuthenticationToken(ServerHttpRequest request) {
        // Token en query param ?
        String tokenFromQuery = request.getQueryParams().getFirst("token");
        if (StringUtils.hasText(tokenFromQuery)) {
            return true;
        }

        // Token en Authorization header ?
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return true;
        }

        // Token en cookie ?
        List<String> cookieHeaders = request.getHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                if (cookieHeader.contains(accessTokenCookieName + "=")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Log les headers WebSocket pour debug
     */
    private void logWebSocketHeaders(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();

        log.debug("📋 WebSocket Headers:");
        log.debug("  Upgrade: {}", headers.getFirst(UPGRADE_HEADER));
        log.debug("  Connection: {}", headers.getFirst(CONNECTION_HEADER));
        log.debug("  Sec-WebSocket-Key: {}", headers.getFirst(SEC_WEBSOCKET_KEY));
        log.debug("  Sec-WebSocket-Version: {}", headers.getFirst(SEC_WEBSOCKET_VERSION));
        log.debug("  Sec-WebSocket-Protocol: {}", headers.getFirst(SEC_WEBSOCKET_PROTOCOL));
        log.debug("  Authorization: {}",
                headers.getFirst(AUTHORIZATION_HEADER) != null ? "Present" : "Absent");
        log.debug("  Origin: {}", headers.getFirst("Origin"));
        log.debug("  User-Agent: {}", headers.getFirst("User-Agent"));

        // Log des query parameters
        if (!request.getQueryParams().isEmpty()) {
            log.debug("  Query Params: {}", request.getQueryParams());
        }
    }

    @Override
    public int getOrder() {
        // S'exécute très tôt mais ne modifie rien
        return -100;
    }
}