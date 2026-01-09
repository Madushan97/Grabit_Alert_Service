# -------- Build stage --------
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

# -------- Runtime stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Set timezone
ENV TZ=Asia/Colombo

EXPOSE 6060

# Set profile explicitly
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
