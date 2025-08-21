package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Filtre de traduction d'authentification pour Gateway - VERSION CORRIGÉE
 *
 * RÔLE : Faire la traduction unidirectionnelle Bearer tokens (mobile) → Cookies (microservices)
 *
 * FLUX ENTRANT UNIQUEMENT (Mobile → Microservices) :
 * 1. Mobile envoie Bearer token
 * 2. Gateway convertit en Cookie pour les microservices
 *
 * NOTE: La traduction retour (Set-Cookie → Bearer) est supprimée car elle causait des conflits
 */
@Slf4j
@Component
public class AuthenticationTranslatorFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String COOKIE_HEADER = "Cookie";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientType = request.getHeaders().getFirst(CLIENT_TYPE_HEADER);
        boolean isMobileClient = "mobile".equals(clientType);

        log.debug("AuthenticationTranslatorFilter - Mobile client: {}", isMobileClient);

        if (isMobileClient) {
            return handleMobileToMicroserviceTranslation(exchange, chain);
        } else {
            return handleWebRequest(exchange, chain);
        }
    }

    /**
     * Gestion des requêtes mobiles : Bearer → Cookie (aller uniquement)
     */
    private Mono<Void> handleMobileToMicroserviceTranslation(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // ÉTAPE 1 : Traduction Bearer Token → Cookie pour les microservices
        ServerHttpRequest modifiedRequest = translateBearerToCookie(request);

        // ÉTAPE 2 : Continuer avec la requête modifiée
        ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();

        // SUPPRESSION : Plus de modification des headers de réponse (causait l'erreur)
        return chain.filter(modifiedExchange);
    }

    /**
     * Conversion Bearer Token → Cookie pour les microservices
     */
    private ServerHttpRequest translateBearerToCookie(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            // Créer le cookie pour les microservices
            String cookieValue = accessTokenCookieName + "=" + token;

            // Ajouter le token comme query param pour Socket.io
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUri(request.getURI());
            if (!request.getQueryParams().containsKey("token")) {
                uriBuilder.queryParam("token", token);
            }

            log.debug("Mobile request: Converting Bearer token to Cookie and query param for microservices");

            return request.mutate()
                    .header(COOKIE_HEADER, cookieValue)
                    .uri(uriBuilder.build(true).toUri())
                    // GARDER le header Authorization pour compatibilité
                    // .headers(headers -> headers.remove(AUTHORIZATION_HEADER))
                    .build();
        }

        // Vérifier si le token est déjà dans un cookie (rétrocompatibilité)
        HttpCookie existingCookie = request.getCookies().getFirst(accessTokenCookieName);
        if (existingCookie != null) {
            log.debug("Mobile request: Token already in cookie format");
            return request;
        }

        log.debug("Mobile request: No authentication token found");
        return request;
    }

    /**
     * Gestion des requêtes web : Passer tel quel (comportement actuel)
     */
    private Mono<Void> handleWebRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.debug("Web request: Passing through without translation");
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1; // Après UnifiedAuthenticationFilter (-2)
    }
}
