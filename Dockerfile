# FROM eclipse-temurin:21-jre
# WORKDIR /app
# COPY target/*.jar app.jar
# ENTRYPOINT ["java", "-jar", "app.jar"]

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom first to cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /app/logs && chown appuser:appgroup /app/logs
USER appuser

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Configuration for JVM inside container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Expose Port
EXPOSE 8000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]