package com.bacos.mokengeli.biloko.filter;

import com.bacos.mokengeli.biloko.monitoring.SocketIoConnectionTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filtre permettant de détecter les requêtes Socket.io et de suivre les connexions WebSocket actives.
 */
@Slf4j
@Component
public class SocketIoRoutingFilter implements GlobalFilter, Ordered {

    private static final String SOCKET_IO_PATH = "/socket.io";
    private final SocketIoConnectionTracker tracker;

    public SocketIoRoutingFilter(SocketIoConnectionTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path.startsWith(SOCKET_IO_PATH)) {
            String transport = exchange.getRequest().getQueryParams().getFirst("transport");
            log.debug("Socket.io request detected - transport: {}", transport);
            if ("websocket".equalsIgnoreCase(transport)) {
                tracker.increment();
                return chain.filter(exchange)
                        .doFinally(signalType -> tracker.decrement());
            }
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Exécuté avant les filtres d'authentification
        return -3;
    }
}
