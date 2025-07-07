package com.bacos.mokengeli.biloko.config;

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
 * Filtre d'authentification hybride supportant Cookie ET Bearer Token
 * - Web: utilise les cookies (comportement actuel préservé)
 * - Mobile: utilise Bearer token dans Authorization header
 */
@Slf4j
@Component
public class HybridAuthenticationFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Vérifier d'abord si un header Authorization existe déjà
        String existingAuthHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(existingAuthHeader) && existingAuthHeader.startsWith(BEARER_PREFIX)) {
            // Le header existe déjà (requête mobile), on continue sans modification
            log.debug("Authorization header already present, proceeding with mobile request");
            return chain.filter(exchange);
        }
        
        // Si pas de header Authorization, vérifier le cookie (requête web)
        String tokenFromCookie = extractTokenFromCookie(request);
        if (StringUtils.hasText(tokenFromCookie)) {
            // Ajouter le token du cookie dans le header Authorization pour les microservices
            log.debug("Token found in cookie, adding to Authorization header");
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header(AUTHORIZATION_HEADER, BEARER_PREFIX + tokenFromCookie)
                    .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        }
        
        // Pas de token trouvé (requête publique ou non authentifiée)
        log.debug("No authentication token found in request");
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
        // S'exécute après le filtre CORS mais avant les autres filtres
        return -2;
    }
}