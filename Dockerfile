# syntax=docker/dockerfile:1.7
ARG JAVA_VERSION=21
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21-alpine

FROM ${MAVEN_IMAGE} AS dependencies
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

FROM dependencies AS builder
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine AS runtime
RUN addgroup -S -g 10001 lumiris && adduser -S -u 10001 -G lumiris lumiris
WORKDIR /app
COPY --from=builder --chown=lumiris:lumiris /build/target/lumiris-backend-*.jar app.jar
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=10 \
    CMD ["wget", "-qO-", "http://localhost:8080/actuator/health/readiness"]
ENTRYPOINT ["java", "-jar", "app.jar"]
