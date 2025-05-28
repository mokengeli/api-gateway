package com.bacos.mokengeli.biloko.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.netty.http.server.HttpServer;
import reactor.netty.tcp.TcpServer;

import java.util.concurrent.TimeUnit;

/**
 * Configuration pour améliorer la gestion des requêtes HTTP mobiles
 */
@Configuration
public class MobileRequestConfig {

    @Bean
    public WebServerFactoryCustomizer<NettyReactiveWebServerFactory> nettyCustomizer() {
        return factory -> factory.addServerCustomizers(this::customizeNettyServer);
    }

    private HttpServer customizeNettyServer(HttpServer httpServer) {
        return httpServer
                // Augmenter la taille des headers pour les requêtes mobiles
                .httpRequestDecoder(spec -> spec
                        .maxHeaderSize(16 * 1024)   // 16 KB au lieu de 8 KB
                        .maxChunkSize(16 * 1024)    // Chunks plus grands
                        .validateHeaders(false)
                )
                // Configuration TCP (keep-alive, no delay, timeout)
                .tcpConfiguration(this::customizeTcpServer);
    }

    private TcpServer customizeTcpServer(TcpServer tcp) {
        return tcp
                // Active TCP keep-alive au niveau socket
                .option(ChannelOption.SO_KEEPALIVE, true)
                // Désactive la mise en tampon Nagle pour réduire la latence
                .option(ChannelOption.TCP_NODELAY, true)
                // Handler Netty pour contrôler l’inactivité totale
                .doOnConnection(conn ->
                        conn.addHandlerLast(
                                new IdleStateHandler(
                                        0,                    // pas de timeout lecture
                                        0,                    // pas de timeout écriture
                                        60,                   // 60 s d’inactivité totale
                                        TimeUnit.SECONDS
                                )
                        )
                );
    }
}
