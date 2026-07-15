package com.drivingschool.backend.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails application startup immediately, with a clear message, if a required secret
 * environment variable is not set - instead of letting the unresolved "${VAR}"
 * placeholder flow into a bean (e.g. the datasource password) and fail later with a
 * confusing downstream error such as a Postgres authentication failure.
 *
 * Runs on {@link ApplicationEnvironmentPreparedEvent}, before any bean is created,
 * so no connection attempts or partial startup work happen first. Registered via
 * META-INF/spring.factories because the ApplicationContext (and therefore component
 * scanning) does not exist yet at this point in the startup lifecycle.
 */
public class RequiredEnvironmentValidator implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final List<String> REQUIRED_VARIABLES = List.of("DB_PASSWORD", "JWT_SECRET");

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        ConfigurableEnvironment environment = event.getEnvironment();

        List<String> missing = new ArrayList<>(REQUIRED_VARIABLES.stream()
                .filter(name -> !StringUtils.hasText(environment.getProperty(name)))
                .toList());

        // Checked against the raw STORAGE_PROVIDER env var, not app.storage.provider:
        // this listener runs at HIGHEST_PRECEDENCE, before application*.yml is loaded,
        // so YAML-derived properties (including the application-prod.yml default of
        // "gcs") are not yet resolved here - only raw environment variables are.
        // GCP_PROJECT_ID is deliberately not required, since Application Default
        // Credentials can auto-detect the project on GCP-hosted environments.
        if ("gcs".equals(environment.getProperty("STORAGE_PROVIDER"))
                && !StringUtils.hasText(environment.getProperty("GCS_BUCKET_NAME"))) {
            missing.add("GCS_BUCKET_NAME");
        }

        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Missing required environment variable(s): " + String.join(", ", missing) +
                            ". These have no default and must be set before starting the application " +
                            "(see .env.example).");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
