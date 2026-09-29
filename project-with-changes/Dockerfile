# syntax=docker/dockerfile:1

# --- Build stage ---
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache dependency resolution in its own layer - only re-runs when pom.xml changes.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src src
# Tests already ran (and gate this job via `needs: test`) in ci.yml's separate
# test job, which has real Docker daemon access for the Testcontainers-based
# tests. This RUN step, by contrast, executes inside a `docker build` layer
# with no daemon access, so it can never run those tests - re-running the
# suite here would be redundant even where it could run. Package only.
RUN mvn -q -B clean package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system appuser && useradd --system --gid appuser --home-dir /app appuser \
    && apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /build/target/driving-school-backend-*.jar app.jar

RUN mkdir -p /app/uploads /app/logs && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# Render uses its own health check (the service's Health Check Path setting -
# see DEPLOYMENT.md) rather than this; it's primarily for docker-compose and
# plain `docker run` local usage.
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -sf "http://localhost:${PORT:-8080}/actuator/health/liveness" || exit 1

# JDK 21 auto-detects container memory limits (UseContainerSupport is default-on);
# MaxRAMPercentage keeps some headroom for non-heap memory rather than claiming it all.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75.0 -jar app.jar"]
