package com.bacos.mokengeli.biloko.monitoring;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Health indicator pour surveiller l'état de la connectivité Socket.io.
 */
@Component
public class SocketIoHealthIndicator implements ReactiveHealthIndicator {

    private final SocketIoConnectionTracker tracker;

    public SocketIoHealthIndicator(SocketIoConnectionTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public Mono<Health> health() {
        return Mono.just(Health.up()
                .withDetail("socketIoActiveConnections", tracker.getActiveConnections())
                .build());
    }
}
