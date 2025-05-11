# Stage 1: Build the application using Maven
# We'll use a Maven image that includes JDK 11
FROM maven:3.8.5-openjdk-11 AS builder

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml file to download dependencies
COPY pom.xml .

# Download all dependencies. This layer is cached unless pom.xml changes.
RUN mvn dependency:go-offline -B

# Copy the rest of the application's source code
COPY src ./src

# Package the application (compile, test, and create an executable JAR)
# The pom.xml should be configured with spring-boot-maven-plugin to build a fat JAR.
RUN mvn package -DskipTests -B

# Stage 2: Create the runtime image
# Use a slim OpenJDK 11 JRE image for a smaller final image size
FROM openjdk:11-jre-slim

# Set the working directory in the container
WORKDIR /app

# Copy the executable JAR file from the builder stage.
# Explicitly naming the JAR based on pom.xml artifactId and version.
# This ensures we're copying the correct Spring Boot repackaged JAR.
COPY --from=builder /app/target/metta-bot-1.0-SNAPSHOT.jar app.jar

# Command to run the application
# Spring Boot's repackaged JARs are executable with 'java -jar'
ENTRYPOINT ["java", "-jar", "app.jar"]
