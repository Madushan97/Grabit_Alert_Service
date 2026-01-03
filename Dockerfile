# Multi-stage Dockerfile for VendingMachineAlertService
# Build stage: use an official Maven image with JDK 17 to build the application
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace
# Copy only pom.xml first to leverage Docker layer cache for dependencies
COPY pom.xml ./
# Copy sources
COPY src ./src
# Build the project (skip tests to keep image build fast; remove -DskipTests for CI builds)
RUN mvn -B -DskipTests package

# Runtime stage: use a lightweight JRE image
FROM eclipse-temurin:17-jre
WORKDIR /app
# Argument to allow overriding if you need a specific jar
ARG JAR_FILE=target/*.jar
# Copy the fat jar from build stage
COPY --from=build /workspace/target/*.jar ./app.jar

# Expose default Spring Boot port
EXPOSE 8080

# Allow runtime options via JAVA_OPTS and override active profile via SPRING_PROFILES_ACTIVE
ENV JAVA_OPTS=""
ENV SPRING_PROFILES_ACTIVE=default

# Use a small startup wrapper so JAVA_OPTS and profile envs are respected
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -Djava.security.egd=file:/dev/./urandom -jar /app/app.jar"]
