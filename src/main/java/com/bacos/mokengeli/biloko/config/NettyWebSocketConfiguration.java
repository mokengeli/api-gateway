package com.bacos.mokengeli.biloko.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.server.HttpServer;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Configuration Netty optimisée pour WebSocket et requêtes mobiles
 * Combine les besoins HTTP et WebSocket
 */
@Slf4j
@Configuration
public class NettyWebSocketConfiguration {

    /**
     * Configuration du serveur Netty pour supporter WebSocket
     */
    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyServerCustomizer() {
        return factory -> {
            factory.addServerCustomizers(httpServer -> {
                log.info("🔧 Configuring Netty server for WebSocket support");

                return httpServer
                        // Configuration HTTP pour supporter les grandes headers (mobile + WebSocket)
                        .httpRequestDecoder(spec -> spec
                                .maxHeaderSize(16 * 1024)          // 16 KB headers
                                .maxInitialLineLength(8 * 1024)    // 8 KB initial line
                                .maxChunkSize(64 * 1024)           // 64 KB chunks
                                .validateHeaders(false)             // Moins strict pour compatibilité
                        )
                        // Options socket pour WebSocket et mobile
                        .option(ChannelOption.SO_KEEPALIVE, true)           // Keep-alive TCP
                        .option(ChannelOption.TCP_NODELAY, true)            // Pas de délai Nagle
                        .option(ChannelOption.SO_REUSEADDR, true)           // Réutilisation rapide du port
                        .option(ChannelOption.SO_BACKLOG, 1000)             // Queue de connexions

                        // Options child channel (par connexion)
                        .childOption(ChannelOption.SO_KEEPALIVE, true)
                        .childOption(ChannelOption.TCP_NODELAY, true)
                        .childOption(ChannelOption.SO_SNDBUF, 1024 * 1024)  // Buffer envoi 1MB
                        .childOption(ChannelOption.SO_RCVBUF, 1024 * 1024)  // Buffer réception 1MB

                        // Gestion de l'idle pour WebSocket (2 minutes)
                        .doOnConnection(conn -> {
                            conn.addHandlerLast(
                                    new IdleStateHandler(
                                            0,                    // Pas de timeout lecture
                                            0,                    // Pas de timeout écriture
                                            120,                  // 120s d'inactivité totale
                                            TimeUnit.SECONDS
                                    )
                            );
                            log.debug("📡 New connection established with idle timeout of 120s");
                        });
            });
        };
    }

    /**
     * Configuration du HttpClient optimisé pour WebSocket
     * Ce bean personnalise le HttpClient utilisé par le Gateway
     */
    @Bean
    public HttpClient gatewayHttpClient() {
        // Provider de connexions optimisé pour WebSocket
        ConnectionProvider provider = ConnectionProvider.builder("websocket-gateway")
                .maxConnections(1000)                    // Max connexions totales
                .maxIdleTime(Duration.ofSeconds(60))     // Idle timeout
                .maxLifeTime(Duration.ofMinutes(10))     // Durée de vie max
                .pendingAcquireTimeout(Duration.ofSeconds(45))  // Timeout acquisition
                .evictInBackground(Duration.ofSeconds(120))     // Nettoyage en arrière-plan
                .build();

        return HttpClient.create(provider)
                .responseTimeout(Duration.ofSeconds(120))       // Timeout réponse (long pour WS)
                .option(ChannelOption.SO_KEEPALIVE, true)       // Keep-alive activé
                .option(ChannelOption.TCP_NODELAY, true)        // Pas de délai Nagle
                .doOnConnected(conn -> {
                    // Configuration de la connexion
                    conn.addHandlerLast(new IdleStateHandler(0, 0, 120, TimeUnit.SECONDS));
                    log.debug("🔗 HTTP client connection configured for WebSocket");
                })
                .wiretap("reactor.netty.http.client",          // Debug logging
                        io.netty.handler.logging.LogLevel.DEBUG);
    }

    /**
     * Bean d'information sur la configuration
     */
    @Bean
    public WebSocketConfigInfo webSocketConfigInfo() {
        WebSocketConfigInfo info = new WebSocketConfigInfo();
        info.setMaxHeaderSize("16KB");
        info.setMaxChunkSize("64KB");
        info.setIdleTimeout("120 seconds");
        info.setMaxConnections(1000);
        info.setSendBuffer("1MB");
        info.setReceiveBuffer("1MB");

        log.info("🔌 WebSocket configuration summary: {}", info);
        return info;
    }

    /**
     * Classe pour exposer la configuration
     */
    public static class WebSocketConfigInfo {
        private String maxHeaderSize;
        private String maxChunkSize;
        private String idleTimeout;
        private int maxConnections;
        private String sendBuffer;
        private String receiveBuffer;

        // Getters and setters
        public String getMaxHeaderSize() { return maxHeaderSize; }
        public void setMaxHeaderSize(String maxHeaderSize) { this.maxHeaderSize = maxHeaderSize; }

        public String getMaxChunkSize() { return maxChunkSize; }
        public void setMaxChunkSize(String maxChunkSize) { this.maxChunkSize = maxChunkSize; }

        public String getIdleTimeout() { return idleTimeout; }
        public void setIdleTimeout(String idleTimeout) { this.idleTimeout = idleTimeout; }

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }

        public String getSendBuffer() { return sendBuffer; }
        public void setSendBuffer(String sendBuffer) { this.sendBuffer = sendBuffer; }

        public String getReceiveBuffer() { return receiveBuffer; }
        public void setReceiveBuffer(String receiveBuffer) { this.receiveBuffer = receiveBuffer; }

        @Override
        public String toString() {
            return "WebSocketConfig{" +
                    "maxHeaderSize='" + maxHeaderSize + '\'' +
                    ", maxChunkSize='" + maxChunkSize + '\'' +
                    ", idleTimeout='" + idleTimeout + '\'' +
                    ", maxConnections=" + maxConnections +
                    ", sendBuffer='" + sendBuffer + '\'' +
                    ", receiveBuffer='" + receiveBuffer + '\'' +
                    '}';
        }
    }
}