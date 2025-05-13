package com.bacos.mokengeli.biloko.config;

import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fournit un HttpMessageConverters afin que le SpringDecoder d’OpenFeign
 * s’initialise même dans une appli WebFlux pure.
 */
@Configuration
public class FeignWebFluxBridgeConfig {

    @Bean
    HttpMessageConverters httpMessageConverters() {
        // liste vide : Feign utilisera quand même la même ObjectMapper,
        // pas besoin de starter‐web complet.
        return new HttpMessageConverters();
    }
}
