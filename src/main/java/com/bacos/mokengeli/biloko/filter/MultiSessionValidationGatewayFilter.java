package com.bacos.mokengeli.biloko.filter;

import com.bacos.mokengeli.biloko.model.SessionListResponse;
import com.bacos.mokengeli.biloko.service.AuthInternalClient;
import com.bacos.mokengeli.biloko.service.SessionCache;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.List;

/**
 * GlobalFilter exécuté sur chaque requête (hors routes publiques) pour vérifier que le JTI porté par le JWT
 * figure toujours dans la liste des sessions actives fournies par l'Authentication-Service.
 * <p>
 * Points clés:
 * 1. Utilise la découverte de service Eureka (WebClient @LoadBalanced) → pas d'URL fixe.
 * 2. Cache Caffeine de 2minutes pour réduire la charge réseau tout en restant réactif aux révocations.
 * 3. Stratégie «no‑token⇒ pass» pour les routes publiques.
 */
@Component
@RequiredArgsConstructor
public class MultiSessionValidationGatewayFilter implements GlobalFilter, Ordered {
    private final ObjectProvider<AuthInternalClient> authClientProvider;   // Lazy fetch to break circular dependency
    private final SessionCache sessionCache;

    @Value("${security.jwt.secret}")
    private String secretKey;

    @Value("${security.jwt.cookie.access-token}")
    private String jwtCookieName;

    @Value("${gateway.public-paths:/public/**}")
    private List<String> publicPaths;
    // = nom enregistré dans Eureka

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String requestPath = exchange.getRequest().getURI().getPath();
        boolean isPublic = publicPaths.stream().anyMatch(p -> p.equals(requestPath));
        HttpCookie jwtCookie = exchange.getRequest().getCookies().getFirst(jwtCookieName);

        // Stratégie «no‑token ⇒ pass» sur les routes publiques
        if (isPublic || jwtCookie == null || jwtCookie.getValue().isEmpty()) {
            return chain.filter(exchange);
        }


        /** if (jwtCookie == null || jwtCookie.getValue().isEmpty()) {
         exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
         return exchange.getResponse().setComplete();
         }*/

        // 1. Parse le JWT en local
        Claims claims;
        try {
            claims = Jwts
                    .parser()
                    .setSigningKey(getSignKey())
                    .build()
                    .parseClaimsJws(jwtCookie.getValue())
                    .getBody();
        } catch (Exception ex) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String employeeNumber = claims.get("employeeNumber", String.class);
        String appType = claims.get("appType", String.class);
        String jti = claims.get("jti", String.class); // ID explicite dans payload

        if (employeeNumber == null || appType == null || jti == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 2. Vérifie le cache
        String cacheKey = employeeNumber + ":" + appType;
        List<String> cachedJtis = sessionCache.get(cacheKey);
        if (cachedJtis != null && cachedJtis.contains(jti)) {
            return chain.filter(exchange); // hit positif → accès direct
        }
        // 3. Interroge l’Authentication‑Service (appel Feign bloquant emballé)
        String cookieHeader = jwtCookieName + "=" + jwtCookie.getValue();
        return Mono.fromCallable(() -> authClientProvider.getObject().list(cookieHeader, employeeNumber, appType))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(resp -> {
                    // Met à jour le cache (liste des jtis valides)
                    sessionCache.put(cacheKey, resp.extractJtis());
                    return validateWithMax(exchange, chain, jti, resp);
                });
    }

    private Mono<Void> validateWithMax(ServerWebExchange ex,
                                       GatewayFilterChain chain,
                                       String jti,
                                       SessionListResponse resp) {
        if (resp.getSessions().stream().anyMatch(s -> jti.equals(s.getJti()))) {
            return chain.filter(ex);                   // jti toujours autorisé
        }
        // jti non trouvé → trop de sessions ?
        if (resp.getSessions().size() >= resp.getMaxSessions()) {
            ex.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return ex.getResponse().setComplete();    // 429 Too Many Connections
        }
        // Autre cas : jti inconnu mais quota libre → 401
        ex.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return ex.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -5; // avant les filtres d'authz locaux
    }

    /* =============================================================
       JWT helpers
       ============================================================= */
    private Key getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }
}

