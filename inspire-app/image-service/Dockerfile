# ── Stage 1: Build + Test ────────────────────────────────────────────────────
# Tests run here. A failing test stops the Docker build — images cannot be
# built from broken code.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM — required so Maven can resolve the multi-module structure
COPY pom.xml /app/pom.xml

# Copy only this module's files to keep the layer cache efficient
COPY image-service/pom.xml /app/image-service/pom.xml
COPY image-service/src      /app/image-service/src

# Build and run tests. Remove -DskipTests to enforce quality gate.
RUN mvn -f /app/image-service/pom.xml clean package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
# Minimal Alpine JRE — no Maven, no source code, no build tools.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/image-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
