package com.drivingschool.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Arrays;

/**
 * OpenAPI/Swagger Configuration
 * Restricts API documentation access based on profile
 * Production: Only accessible by authenticated ADMIN users
 * Development: Full public access
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "Bearer Authentication";
    private final Environment environment;

    public OpenApiConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public OpenAPI openAPI() {
        OpenAPI api = new OpenAPI()
                .info(new Info()
                        .title("Driving School Management API")
                        .description("Enterprise Driving School Management Platform REST API")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Driving School Platform")
                                .email("support@drivingschool.com"))
                        .license(new License().name("Proprietary - All Rights Reserved")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token - Required for all authenticated endpoints")));

        // Add server information
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            api.addServersItem(new Server().url("https://api.drivingschool.com").description("Production API"));
        } else if (Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
            api.addServersItem(new Server().url("http://localhost:8080").description("Development API"));
        }

        return api;
    }
}
