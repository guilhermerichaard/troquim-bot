# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY mvnw mvnw.cmd pom.xml ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

RUN ./mvnw clean package -DskipTests -B

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=azure
ENV CONVERSATION_MODE=STRICT_MVP

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
