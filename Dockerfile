# =============================================================
# Dockerfile – Workout Plan Management System
# Multi-stage build: Maven build + minimal JRE runtime image
# =============================================================

# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy POM first for layer caching – dependencies are re-downloaded
# only when pom.xml changes, not on every code change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source code and build the JAR (skip Selenium tests during Docker build)
COPY src ./src
RUN mvn clean package -DskipTests -q

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy

LABEL maintainer="DevOps College Project"
LABEL version="1.0.0"
LABEL description="Dockerized Workout Plan Management System"

# Create a non-root user for security
RUN groupadd -r fitflow && useradd -r -g fitflow fitflow

WORKDIR /app

# Copy the built JAR from the builder stage
COPY --from=builder /build/target/workout-plan-system-1.0.0.jar app.jar

# Set ownership
RUN chown -R fitflow:fitflow /app

USER fitflow

# Application port
EXPOSE 8080

# Health check – Docker will mark container as healthy after the endpoint responds
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
