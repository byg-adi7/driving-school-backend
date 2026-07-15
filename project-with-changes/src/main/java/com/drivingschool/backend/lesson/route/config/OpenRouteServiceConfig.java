package com.drivingschool.backend.lesson.route.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openroute")
@Data
public class OpenRouteServiceConfig {

    private String apiKey;
    private String directionsUrl = "https://api.openrouteservice.org/v2/directions/driving-car";
    private Integer timeout = 5000;
}
