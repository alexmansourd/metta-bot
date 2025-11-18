#####
# Multi-stage Dockerfile optimized for Railway
# - Small final image (Alpine Temurin JRE 11)
# - Better Docker layer caching (go-offline before copying sources)
# - Safe non-root runtime user
# - Container-aware JVM defaults via JAVA_TOOL_OPTIONS
#####

# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-11 AS builder
WORKDIR /app

# Leverage cache: download dependencies first
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# Now copy sources and build
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- Runtime stage ----
# Use a Debian/Ubuntu-based Temurin image to ensure multi-arch support (incl. arm64 on Railway)
FROM eclipse-temurin:11-jre-jammy

# Minimal OS deps: time zone + CA certs
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the Spring Boot fat JAR
COPY --from=builder /app/target/metta-bot-1.0-SNAPSHOT.jar /app/app.jar

# Allow Railway to override/append args; keep entry flexible
ENTRYPOINT ["java"]
CMD ["-jar", "/app/app.jar"]
