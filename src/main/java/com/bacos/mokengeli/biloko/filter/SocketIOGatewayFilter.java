package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Filtre spécialisé pour gérer le routage Socket.io à travers l'API Gateway
 * Socket.io nécessite un traitement spécial car il utilise à la fois HTTP polling et WebSocket
 */
@Slf4j
@Component
public class SocketIOGatewayFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String SOCKET_IO_PATH = "/socket.io/";
    private static final String TRANSPORT_PARAM = "transport";
    private static final String EIO_PARAM = "EIO";  // Engine.IO version
    private static final String SID_PARAM = "sid";  // Session ID Socket.io
    private static final String TOKEN_PARAM = "token";
    private static final String UPGRADE_HEADER = "Upgrade";
    private static final String CONNECTION_HEADER = "Connection";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();

        // Détecter les requêtes Socket.io
        if (!isSocketIORequest(path)) {
            return chain.filter(exchange);
        }

        log.info("🔌 Socket.io request detected: {} {}", request.getMethod(), path);
        
        // Analyser le type de transport Socket.io
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        String transport = queryParams.getFirst(TRANSPORT_PARAM);
        String eio = queryParams.getFirst(EIO_PARAM);
        String sid = queryParams.getFirst(SID_PARAM);
        
        log.debug("Socket.io params - Transport: {}, EIO: {}, SID: {}", transport, eio, sid);

        // Déterminer le type de requête Socket.io
        boolean isWebSocketUpgrade = isWebSocketUpgrade(request);
        boolean isPolling = "polling".equals(transport);
        
        if (isWebSocketUpgrade) {
            log.info("🚀 Socket.io WebSocket upgrade request");
            return handleSocketIOWebSocket(exchange, chain);
        } else if (isPolling) {
            log.info("📡 Socket.io polling request");
            return handleSocketIOPolling(exchange, chain);
        } else {
            log.info("📦 Socket.io initial handshake request");
            return handleSocketIOHandshake(exchange, chain);
        }
    }

    /**
     * Détecte si c'est une requête Socket.io
     */
    private boolean isSocketIORequest(String path) {
        return path.contains(SOCKET_IO_PATH) || 
               path.contains("/api/order/socket.io/") ||
               path.contains("/api/order/socketio/");
    }

    /**
     * Vérifie si c'est un upgrade WebSocket
     */
    private boolean isWebSocketUpgrade(ServerHttpRequest request) {
        String upgrade = request.getHeaders().getFirst(UPGRADE_HEADER);
        String connection = request.getHeaders().getFirst(CONNECTION_HEADER);
        
        return "websocket".equalsIgnoreCase(upgrade) &&
               connection != null && connection.toLowerCase().contains("upgrade");
    }

    /**
     * Gère l'upgrade WebSocket pour Socket.io
     */
    private Mono<Void> handleSocketIOWebSocket(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // S'assurer que le token est présent pour l'authentification
        String token = extractToken(request);
        
        if (!StringUtils.hasText(token)) {
            log.warn("⚠️ No token found for Socket.io WebSocket upgrade");
            // Socket.io gère sa propre auth, on laisse passer
        } else {
            log.debug("✅ Token found for Socket.io WebSocket: {}", token.substring(0, Math.min(token.length(), 10)) + "...");
        }
        
        // Logger les headers WebSocket importants
        logWebSocketHeaders(request);
        
        // Ne pas modifier la requête, laisser Spring Cloud Gateway gérer le proxy WebSocket
        return chain.filter(exchange);
    }

    /**
     * Gère les requêtes de polling Socket.io
     */
    private Mono<Void> handleSocketIOPolling(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Pour le polling, s'assurer que les cookies/headers d'auth sont présents
        String token = extractToken(request);
        
        if (StringUtils.hasText(token)) {
            // Si on a un token dans les query params, l'ajouter aux headers
            if (request.getQueryParams().containsKey(TOKEN_PARAM)) {
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header("Authorization", "Bearer " + token)
                    .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        }
        
        return chain.filter(exchange);
    }

    /**
     * Gère le handshake initial Socket.io
     */
    private Mono<Void> handleSocketIOHandshake(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        log.debug("Socket.io handshake - Method: {}, Path: {}", 
                 request.getMethod(), request.getPath());
        
        // Pour le handshake, s'assurer que l'authentification est disponible
        String token = extractToken(request);
        
        if (StringUtils.hasText(token)) {
            // Ajouter le token aux headers si pas déjà présent
            if (!request.getHeaders().containsKey("Authorization")) {
                ServerHttpRequest modifiedRequest = request.mutate()
                    .header("Authorization", "Bearer " + token)
                    .build();
                
                return chain.filter(exchange.mutate().request(modifiedRequest).build());
            }
        }
        
        return chain.filter(exchange);
    }

    /**
     * Extrait le token JWT de différentes sources
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Query parameter (priorité pour Socket.io)
        String tokenFromQuery = request.getQueryParams().getFirst(TOKEN_PARAM);
        if (StringUtils.hasText(tokenFromQuery)) {
            return tokenFromQuery;
        }
        
        // 2. Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        
        // 3. Cookie
        List<String> cookieHeaders = request.getHeaders().get("Cookie");
        if (cookieHeaders != null) {
            for (String cookieHeader : cookieHeaders) {
                String[] cookies = cookieHeader.split(";");
                for (String cookie : cookies) {
                    cookie = cookie.trim();
                    if (cookie.startsWith(accessTokenCookieName + "=")) {
                        return cookie.substring(accessTokenCookieName.length() + 1);
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Log les headers WebSocket pour debug
     */
    private void logWebSocketHeaders(ServerHttpRequest request) {
        HttpHeaders headers = request.getHeaders();
        
        log.debug("📋 Socket.io WebSocket Headers:");
        log.debug("  Upgrade: {}", headers.getFirst(UPGRADE_HEADER));
        log.debug("  Connection: {}", headers.getFirst(CONNECTION_HEADER));
        log.debug("  Sec-WebSocket-Version: {}", headers.getFirst("Sec-WebSocket-Version"));
        log.debug("  Sec-WebSocket-Key: {}", headers.getFirst("Sec-WebSocket-Key"));
        log.debug("  Sec-WebSocket-Extensions: {}", headers.getFirst("Sec-WebSocket-Extensions"));
        log.debug("  Origin: {}", headers.getFirst("Origin"));
        
        // Log des query params Socket.io
        MultiValueMap<String, String> queryParams = request.getQueryParams();
        log.debug("  Query Params: {}", queryParams);
    }

    @Override
    public int getOrder() {
        // S'exécute très tôt pour capturer les requêtes Socket.io
        return -200;
    }
}