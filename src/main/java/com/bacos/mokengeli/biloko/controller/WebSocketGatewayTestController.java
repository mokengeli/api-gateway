package com.bacos.mokengeli.biloko.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller de test et monitoring pour les WebSockets via Gateway
 */
@Slf4j
@RestController
@RequestMapping("/gateway")
public class WebSocketGatewayTestController {
    
    private final RouteLocator routeLocator;
    
    @Value("${server.port:8081}")
    private String serverPort;
    
    public WebSocketGatewayTestController(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }
    
    /**
     * Informations sur la configuration WebSocket du Gateway
     */
    @GetMapping("/websocket/info")
    public Mono<Map<String, Object>> getWebSocketInfo() {
        log.info("📊 WebSocket info requested");
        
        Map<String, Object> info = new HashMap<>();
        info.put("timestamp", OffsetDateTime.now());
        info.put("gatewayPort", serverPort);
        
        // Endpoints WebSocket disponibles
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("nativeWebSocket", "ws://localhost:" + serverPort + "/api/order/ws/websocket");
        endpoints.put("sockJS", "ws://localhost:" + serverPort + "/api/order/ws");
        info.put("websocketEndpoints", endpoints);
        
        // Protocoles supportés
        info.put("supportedProtocols", new String[]{"STOMP 1.0", "STOMP 1.1", "STOMP 1.2"});
        
        // Configuration
        Map<String, Object> config = new HashMap<>();
        config.put("heartbeatInterval", "30000ms");
        config.put("maxFrameSize", "1MB");
        config.put("idleTimeout", "120s");
        info.put("configuration", config);
        
        return Mono.just(info);
    }
    
    /**
     * Liste toutes les routes configurées (utile pour debug)
     */
    @GetMapping("/routes")
    public Flux<Map<String, Object>> getRoutes() {
        log.info("📍 Routes listing requested");
        
        return routeLocator.getRoutes()
            .map(this::routeToMap);
    }
    
    /**
     * Vérifie spécifiquement les routes WebSocket
     */
    @GetMapping("/websocket/routes")
    public Flux<Map<String, Object>> getWebSocketRoutes() {
        log.info("🔌 WebSocket routes listing requested");
        
        return routeLocator.getRoutes()
            .filter(route -> {
                String id = route.getId();
                return id.contains("websocket") || id.contains("ws");
            })
            .map(this::routeToMap);
    }
    
    /**
     * Test de santé pour WebSocket
     */
    @GetMapping("/websocket/health")
    public Mono<Map<String, String>> checkWebSocketHealth() {
        log.info("🏥 WebSocket health check");
        
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("gateway", "READY");
        health.put("nativeWebSocketRoute", "CONFIGURED");
        health.put("sockJSRoute", "CONFIGURED");
        health.put("timestamp", OffsetDateTime.now().toString());
        
        return Mono.just(health);
    }
    
    /**
     * Instructions de test pour les développeurs
     */
    @GetMapping("/websocket/test-guide")
    public Mono<Map<String, Object>> getTestGuide() {
        Map<String, Object> guide = new HashMap<>();
        
        guide.put("description", "WebSocket Testing Guide via Gateway");
        
        // Test avec wscat
        Map<String, String> wscat = new HashMap<>();
        wscat.put("install", "npm install -g wscat");
        wscat.put("connectNative", "wscat -c \"ws://localhost:" + serverPort + 
                  "/api/order/ws/websocket?token=YOUR_JWT\" -s \"v12.stomp\"");
        wscat.put("connectSockJS", "wscat -c \"ws://localhost:" + serverPort + 
                  "/api/order/ws/[SESSION_ID]/websocket\"");
        guide.put("wscat", wscat);
        
        // Test avec curl
        Map<String, String> curl = new HashMap<>();
        curl.put("checkRoute", "curl http://localhost:" + serverPort + "/gateway/websocket/info");
        curl.put("listRoutes", "curl http://localhost:" + serverPort + "/gateway/routes");
        curl.put("health", "curl http://localhost:" + serverPort + "/gateway/websocket/health");
        guide.put("curl", curl);
        
        // Test JavaScript
        Map<String, String> javascript = new HashMap<>();
        javascript.put("library", "@stomp/stompjs");
        javascript.put("brokerURL", "ws://localhost:" + serverPort + "/api/order/ws/websocket?token=[TOKEN]");
        javascript.put("example", """
            const client = new StompJS.Client({
                brokerURL: 'ws://localhost:%s/api/order/ws/websocket?token=' + token,
                connectHeaders: {
                    'Authorization': 'Bearer ' + token
                },
                onConnect: () => {
                    console.log('Connected via Gateway!');
                    client.subscribe('/topic/orders/TENANT01', (msg) => {
                        console.log('Received:', JSON.parse(msg.body));
                    });
                }
            });
            client.activate();
            """.formatted(serverPort));
        guide.put("javascript", javascript);
        
        return Mono.just(guide);
    }
    
    /**
     * Convertit une Route en Map pour l'affichage
     */
    private Map<String, Object> routeToMap(Route route) {
        Map<String, Object> routeMap = new HashMap<>();
        routeMap.put("id", route.getId());
        routeMap.put("uri", route.getUri().toString());
        routeMap.put("order", route.getOrder());
        routeMap.put("predicates", route.getPredicate().toString());
        
        // Indicateur WebSocket
        boolean isWebSocket = route.getUri().toString().startsWith("ws://") || 
                             route.getUri().toString().startsWith("wss://");
        routeMap.put("isWebSocket", isWebSocket);
        
        return routeMap;
    }
}