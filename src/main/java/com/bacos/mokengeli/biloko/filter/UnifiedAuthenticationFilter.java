package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * UnifiedAuthenticationFilter - Version adaptée pour travailler avec AuthenticationTranslatorFilter
 *
 * RÔLE : Détection et préparation initiale de l'authentification
 * - Mobile : Détecte et laisse AuthenticationTranslatorFilter gérer la traduction
 * - Web : Convertit cookies vers Authorization header directement
 */
@Slf4j
@Component
public class UnifiedAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String USER_AGENT_HEADER = "User-Agent";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Détection du type de client
        String clientType = request.getHeaders().getFirst(CLIENT_TYPE_HEADER);
        String userAgent = request.getHeaders().getFirst(USER_AGENT_HEADER);
        boolean isMobileClient = isMobileRequest(clientType, userAgent);

        log.debug("UnifiedAuthenticationFilter - Client-Type: {}, Mobile: {}", clientType, isMobileClient);

        // Vérifier d'abord si un header Authorization existe déjà
        String existingAuthHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(existingAuthHeader) && existingAuthHeader.startsWith(BEARER_PREFIX)) {
            log.debug("Authorization header already present, proceeding");
            return chain.filter(exchange);
        }

        // Stratégie basée sur le type de client
        if (isMobileClient) {
            return handleMobileAuthentication(exchange, chain, request);
        } else {
            return handleWebAuthentication(exchange, chain, request);
        }
    }

    private boolean isMobileRequest(String clientType, String userAgent) {
        // Vérification explicite du header X-Client-Type
        if ("mobile".equals(clientType)) {
            return true;
        }

        // Détection par User-Agent pour Expo/React Native
        if (userAgent != null) {
            String userAgentLower = userAgent.toLowerCase();
            return userAgentLower.contains("expo") ||
                    userAgentLower.contains("react native") ||
                    userAgentLower.contains("okhttp") ||
                    userAgentLower.contains("mokengelibiloko");
        }

        return false;
    }

    private Mono<Void> handleMobileAuthentication(ServerWebExchange exchange,
                                                  GatewayFilterChain chain,
                                                  ServerHttpRequest request) {
        log.debug("Mobile client detected - AuthenticationTranslatorFilter will handle translation");

        // Pour mobile, on laisse AuthenticationTranslatorFilter gérer la traduction Bearer → Cookie
        // On passe la requête telle quelle
        return chain.filter(exchange);
    }

    private Mono<Void> handleWebAuthentication(ServerWebExchange exchange,
                                               GatewayFilterChain chain,
                                               ServerHttpRequest request) {
        log.debug("Web client detected - handling cookie authentication");

        // Pour web, convertir directement cookie vers Authorization header
        String tokenFromCookie = extractTokenFromCookie(request);
        if (StringUtils.hasText(tokenFromCookie)) {
            log.debug("Web client - Token found in cookie, adding to Authorization header");
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + tokenFromCookie)
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }

        log.debug("Web client - No authentication token found");
        return chain.filter(exchange);
    }

    private String extractTokenFromCookie(ServerHttpRequest request) {
        HttpCookie cookie = request.getCookies().getFirst(accessTokenCookieName);
        if (cookie != null && StringUtils.hasText(cookie.getValue())) {
            return cookie.getValue();
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -2; // Avant AuthenticationTranslatorFilter (-1)
    }
}