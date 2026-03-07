# ── Stage 1: Build + Test ────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml /app/pom.xml
COPY phrase-service/pom.xml /app/phrase-service/pom.xml
COPY phrase-service/src      /app/phrase-service/src

RUN mvn -f /app/phrase-service/pom.xml clean package

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/phrase-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]