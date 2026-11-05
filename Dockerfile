# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

RUN chmod +x mvnw \
    && ./mvnw -B dependency:go-offline -DskipTests

COPY src src

RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S cartok \
    && adduser -S cartok -G cartok

COPY --from=build --chown=cartok:cartok /workspace/target/cartok-api-*.jar app.jar

USER cartok

# Cloud Run injects PORT; tune heap for container memory limits.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar --server.port=${PORT:-8080}"]
