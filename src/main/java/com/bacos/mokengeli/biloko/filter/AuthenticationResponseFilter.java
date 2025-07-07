package com.bacos.mokengeli.biloko.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Filtre pour gérer les réponses d'authentification
 * - Pour les requêtes web : maintient le comportement actuel (cookies)
 * - Pour les requêtes mobile : ajoute le token dans le header Authorization de la réponse
 */
@Slf4j
@Component
public class AuthenticationResponseFilter implements GlobalFilter, Ordered {

    @Value("${security.jwt.cookie.access-token:accessToken}")
    private String accessTokenCookieName;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();
        
        // Appliquer uniquement sur les endpoints d'authentification
        if (!path.contains("/api/auth/login") && !path.contains("/api/auth/refresh")) {
            return chain.filter(exchange);
        }

        // Déterminer si c'est une requête mobile
        boolean isMobileRequest = isMobileRequest(exchange);
        
        return chain.filter(exchange.mutate()
                .response(decorateResponse(exchange, isMobileRequest))
                .build());
    }

    private ServerHttpResponse decorateResponse(ServerWebExchange exchange, boolean isMobileRequest) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        
        return new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
                if (body instanceof Flux && originalResponse.getStatusCode() == HttpStatus.OK) {
                    Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
                    
                    return super.writeWith(fluxBody.buffer().map(dataBuffers -> {
                        DataBuffer joinedBuffer = originalResponse.bufferFactory().join(dataBuffers);
                        byte[] content = new byte[joinedBuffer.readableByteCount()];
                        joinedBuffer.read(content);
                        DataBufferUtils.release(joinedBuffer);
                        
                        try {
                            String responseBody = new String(content, StandardCharsets.UTF_8);
                            JsonNode jsonNode = objectMapper.readTree(responseBody);
                            
                            // Extraire le token de la réponse
                            String token = null;
                            if (jsonNode.has("token")) {
                                token = jsonNode.get("token").asText();
                            } else if (jsonNode.has("accessToken")) {
                                token = jsonNode.get("accessToken").asText();
                            }
                            
                            if (token != null && isMobileRequest) {
                                // Pour mobile : ajouter le token dans le header Authorization
                                log.debug("Adding Authorization header for mobile response");
                                originalResponse.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                                // Exposer le header pour que le client puisse le lire
                                originalResponse.getHeaders().add("Access-Control-Expose-Headers", "Authorization");
                            }
                            
                        } catch (Exception e) {
                            log.error("Error processing authentication response", e);
                        }
                        
                        return originalResponse.bufferFactory().wrap(content);
                    }));
                }
                return super.writeWith(body);
            }
        };
    }

    private boolean isMobileRequest(ServerWebExchange exchange) {
        String userAgent = exchange.getRequest().getHeaders().getFirst(HttpHeaders.USER_AGENT);
        String origin = exchange.getRequest().getHeaders().getFirst("Origin");
        
        // Détection basique des requêtes mobiles
        if (userAgent != null) {
            String lowerUserAgent = userAgent.toLowerCase();
            if (lowerUserAgent.contains("expo") || 
                lowerUserAgent.contains("react native") ||
                lowerUserAgent.contains("okhttp") ||
                lowerUserAgent.contains("dart") ||
                lowerUserAgent.contains("flutter")) {
                return true;
            }
        }
        
        // Vérifier aussi l'origine
        if (origin != null && (origin.startsWith("exp://") || origin.startsWith("exps://"))) {
            return true;
        }
        
        return false;
    }

    @Override
    public int getOrder() {
        // S'exécute après l'authentification
        return 10;
    }
    
    // Classe helper pour décorer la réponse
    private static class ServerHttpResponseDecorator extends org.springframework.http.server.reactive.ServerHttpResponseDecorator {
        public ServerHttpResponseDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }
    }
}