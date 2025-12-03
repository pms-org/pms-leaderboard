# Build stage
FROM eclipse-temurin:17 AS build
WORKDIR /workspace
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -ntp dependency:go-offline
COPY src ./src
RUN ./mvnw -q -DskipTests package

# Run stage
FROM eclipse-temurin:17-jre
EXPOSE 8080
ARG JAR_FILE=target/*.jar
COPY --from=build /workspace/${JAR_FILE} app.jar
ENTRYPOINT ["java","-jar","/app.jar"]