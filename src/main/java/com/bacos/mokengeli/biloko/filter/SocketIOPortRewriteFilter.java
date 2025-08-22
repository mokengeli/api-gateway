package com.bacos.mokengeli.biloko.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Filtre personnalisé pour rediriger les requêtes Socket.io vers le port 9092
 * après résolution Eureka
 */
@Slf4j
//@Component
public class SocketIOPortRewriteFilter extends AbstractGatewayFilterFactory<SocketIOPortRewriteFilter.Config> {

    public SocketIOPortRewriteFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return new GatewayFilter() {
            @Override
            public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
                // Récupérer l'URI résolu par Eureka
                URI requestUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
                
                if (requestUrl == null) {
                    return chain.filter(exchange);
                }
                
                try {
                    // Remplacer le port par 9092 pour Socket.io
                    URI newUri = new URI(
                        requestUrl.getScheme(),
                        requestUrl.getUserInfo(),
                        requestUrl.getHost(),
                        config.getPort(), // Port Socket.io
                        requestUrl.getPath(),
                        requestUrl.getQuery(),
                        requestUrl.getFragment()
                    );
                    
                    log.debug("Socket.io routing: {} -> {}", requestUrl, newUri);
                    
                    // Mettre à jour l'URI
                    exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, newUri);
                    
                } catch (URISyntaxException e) {
                    log.error("Error rewriting Socket.io URI", e);
                }
                
                return chain.filter(exchange);
            }
        };
    }

    public static class Config {
        private int port = 9092;
        
        public int getPort() {
            return port;
        }
        
        public void setPort(int port) {
            this.port = port;
        }
    }
}