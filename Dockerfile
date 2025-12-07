# FROM eclipse-temurin:17 AS build
# WORKDIR /workspace
# COPY target/*.jar app.jar

# FROM eclipse-temurin:17-jre
# WORKDIR /workspace

# # Install netcat for wait-for-it
# RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# # Copy app from build stage
# COPY --from=build /workspace/app.jar app.jar

# # Copy wait script
# COPY wait-for-it.sh /wait-for-it.sh
# RUN chmod +x /wait-for-it.sh

# EXPOSE 8080

# ENTRYPOINT ["/wait-for-it.sh", "postgres:5432", "--", "java", "-jar", "/workspace/app.jar"]
# FROM eclipse-temurin:21 AS build
# WORKDIR /workspace
# COPY target/*.jar app.jar

# FROM eclipse-temurin:21-jre
# WORKDIR /workspace

# RUN apt-get update && apt-get install -y netcat-openbsd && rm -rf /var/lib/apt/lists/*

# COPY --from=build /workspace/app.jar app.jar
# COPY wait-for-it.sh /wait-for-it.sh
# RUN chmod +x /wait-for-it.sh

# EXPOSE 8080
# ENTRYPOINT ["/wait-for-it.sh", "postgres:5432", "--", "java", "-jar", "/workspace/app.jar"]

FROM eclipse-temurin:21 AS build
WORKDIR /workspace
COPY . .
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /workspace
COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
