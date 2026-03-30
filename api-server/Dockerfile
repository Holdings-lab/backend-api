# Multi-stage build for Spring Boot application
# Stage 1: Build
FROM gradle:8.7-jdk17 AS builder

WORKDIR /build

# Copy gradle files
COPY settings.gradle build.gradle ./
COPY gradle/ ./gradle/
COPY gradlew gradlew.bat ./

# Copy source code
COPY src/ ./src/

# Build application
RUN chmod +x gradlew && ./gradlew build -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy built jar from builder
COPY --from=builder /build/build/libs/*.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:${SERVER_PORT:-8080}/health || exit 1

# Environment variables (can be overridden)
ENV SERVER_PORT=8080
ENV POSTGRES_HOST=postgres
ENV POSTGRES_PORT=5432
ENV POSTGRES_DB=holdings
ENV POSTGRES_USER=postgres
ENV POSTGRES_PASSWORD=postgres
ENV WEBHOOK_SECRET=ai-engine-secure-webhook-secret-key-2026

# Expose port
EXPOSE ${SERVER_PORT}

# Run application with UTF-8 encoding
ENTRYPOINT ["java", \
  "-Dfile.encoding=UTF-8", \
  "-Dsun.jnu.encoding=UTF-8", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
