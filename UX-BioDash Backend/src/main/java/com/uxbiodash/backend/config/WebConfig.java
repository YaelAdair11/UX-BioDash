package com.uxbiodash.backend.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ProcesamientoProperties.class)
public class WebConfig implements WebMvcConfigurer {

    private final String[] origenesPermitidos;

    public WebConfig(@Value("${uxbiodash.cors.origenes-permitidos:http://localhost:3000}") String origenes) {
        this.origenesPermitidos = Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                // Patrones: admiten comodines de puerto (http://localhost:*), y localhost y 127.0.0.1 son
                // origenes distintos para el navegador; sin ambos, PUT/POST/DELETE responden 403.
                .allowedOriginPatterns(origenesPermitidos)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
