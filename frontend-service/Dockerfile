# ── Stage 1: Build + Test ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml /app/pom.xml
COPY frontend-service/pom.xml /app/frontend-service/pom.xml
COPY frontend-service/src      /app/frontend-service/src

RUN mvn -f /app/frontend-service/pom.xml clean package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/frontend-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]