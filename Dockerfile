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
RUN mvn -B -q dependency:go-offline

# Build the JAR. Tests are NOT run here: unit/integration/Selenium tests run in
# the CI pipeline BEFORE this image is built and gate the pipeline.
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:21-jre-jammy

ARG APP_VERSION=dev
LABEL maintainer="DevOps College Project" \
      version="${APP_VERSION}" \
      description="Dockerized Workout Plan Management System"

# Non-root user (with a fixed UID so volume permissions are predictable)
RUN groupadd -r -g 10001 fitflow && useradd -r -u 10001 -g fitflow -M -s /usr/sbin/nologin fitflow

WORKDIR /app

# Version-independent copy: pom.xml may change the version without breaking the build
COPY --from=builder --chown=fitflow:fitflow /build/target/workout-plan-system-*.jar app.jar

USER fitflow

EXPOSE 8080

# JVM tuning for containers (heap is sized from the container memory limit)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Healthy only when the actuator reports status UP (not merely when the port answers)
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# exec => the JVM is PID 1 and receives SIGTERM for a graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
