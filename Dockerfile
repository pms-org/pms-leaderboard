#  Stage 1: Build the Spring Boot application
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /app

COPY ./pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build the JAR
COPY ./src ./src
RUN mvn package -DskipTests

#  Stage 2: Run the application
FROM eclipse-temurin:21

WORKDIR /app

# Copy JAR from the build stage
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8000

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]