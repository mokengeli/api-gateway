package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtre de traduction d'authentification pour Gateway
 *
 * RÔLE : Faire la traduction bidirectionnelle entre Bearer tokens (mobile) et Cookies (microservices)
 *
 * FLUX ENTRANT (Mobile → Microservices) :
 * 1. Mobile envoie Bearer token
 * 2. Gateway convertit en Cookie pour les microservices
 *
 * FLUX SORTANT (Microservices → Mobile) :
 * 1. Microservices répondent avec Set-Cookie
 * 2. Gateway convertit en Bearer token dans les headers de réponse pour mobile
 */
@Slf4j
@Component
public class AuthenticationTranslatorFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String SET_COOKIE_HEADER = "Set-Cookie";
    private static final String COOKIE_HEADER = "Cookie";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String clientType = request.getHeaders().getFirst(CLIENT_TYPE_HEADER);
        boolean isMobileClient = "mobile".equals(clientType);

        log.debug("Processing authentication translation - Mobile client: {}", isMobileClient);

        if (isMobileClient) {
            return handleMobileToMicroserviceTranslation(exchange, chain);
        } else {
            return handleWebRequest(exchange, chain);
        }
    }

    /**
     * Gestion des requêtes mobiles : Bearer → Cookie (aller) et Set-Cookie → Bearer (retour)
     */
    private Mono<Void> handleMobileToMicroserviceTranslation(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // ÉTAPE 1 : Traduction Bearer Token → Cookie pour les microservices
        ServerHttpRequest modifiedRequest = translateBearerToCookie(request);

        // ÉTAPE 2 : Continuer avec la requête modifiée et intercepter la réponse
        ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();

        return chain.filter(modifiedExchange)
                .then(Mono.fromRunnable(() -> {
                    // ÉTAPE 3 : Traduction Set-Cookie → Bearer Token pour la réponse mobile
                    translateSetCookieToBearer(exchange.getResponse());
                }));
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

            log.debug("Mobile request: Converting Bearer token to Cookie for microservices");

            return request.mutate()
                    .header(COOKIE_HEADER, cookieValue)
                    // Optionnel : supprimer le header Authorization pour éviter la confusion
                    .headers(headers -> headers.remove(AUTHORIZATION_HEADER))
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
     * Conversion Set-Cookie → Bearer Token pour les réponses mobiles
     */
    private void translateSetCookieToBearer(ServerHttpResponse response) {
        // Récupérer les cookies de la réponse
        String setCookieHeader = response.getHeaders().getFirst(SET_COOKIE_HEADER);

        if (StringUtils.hasText(setCookieHeader)) {
            // Extraire le token du Set-Cookie
            String token = extractTokenFromSetCookie(setCookieHeader);

            if (StringUtils.hasText(token)) {
                log.debug("Microservice response: Converting Set-Cookie to Bearer token for mobile");

                // Ajouter le Bearer token dans les headers de réponse
                response.getHeaders().add(AUTHORIZATION_HEADER, BEARER_PREFIX + token);

                // Optionnel : Supprimer le Set-Cookie pour mobile (éviter la confusion)
                response.getHeaders().remove(SET_COOKIE_HEADER);

                // Ajouter un header personnalisé pour debug
                response.getHeaders().add("X-Auth-Source", "translated-from-cookie");
            }
        }
    }

    /**
     * Gestion des requêtes web : Passer tel quel (comportement actuel)
     */
    private Mono<Void> handleWebRequest(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.debug("Web request: Passing through without translation");
        return chain.filter(exchange);
    }

    /**
     * Extraire le token du header Set-Cookie
     */
    private String extractTokenFromSetCookie(String setCookieHeader) {
        if (setCookieHeader.contains(accessTokenCookieName + "=")) {
            // Format: accessToken=eyJhbGciOiJIUzI1NiJ9...; Path=/; HttpOnly
            String[] parts = setCookieHeader.split(";");
            for (String part : parts) {
                part = part.trim();
                if (part.startsWith(accessTokenCookieName + "=")) {
                    return part.substring((accessTokenCookieName + "=").length());
                }
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        return -1; // Après HybridAuthenticationFilter (-2) mais avant les autres filtres
    }
}
