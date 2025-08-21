package com.bacos.mokengeli.biloko.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracker simple pour surveiller le nombre de connexions Socket.io actives.
 */
@Component
public class SocketIoConnectionTracker {

    private final AtomicInteger activeConnections = new AtomicInteger();

    public SocketIoConnectionTracker(MeterRegistry meterRegistry) {
        meterRegistry.gauge("socketio.active.connections", activeConnections);
    }

    public void increment() {
        activeConnections.incrementAndGet();
    }

    public void decrement() {
        activeConnections.decrementAndGet();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }
}
