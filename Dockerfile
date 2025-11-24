# ---- Build stage ----
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Leverage cache: download dependencies first
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Now copy sources and build with tests
COPY src ./src
RUN mvn -B package

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre-jammy

# Minimal OS deps: time zone + CA certs
RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the Spring Boot fat JAR
COPY --from=builder /app/target/metta-bot-1.0.jar /app/app.jar

# Allow Railway to override/append args; keep entry flexible
ENTRYPOINT ["java"]
CMD ["-jar", "/app/app.jar"]
