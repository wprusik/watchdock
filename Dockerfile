FROM 4.0.0-rc-5-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/watchdock

RUN apk add --no-cache docker-cli \
    && addgroup -S watchdock \
    && adduser -S -G watchdock watchdock

COPY --from=build /workspace/target/watchdock-1.0-SNAPSHOT.jar /opt/watchdock/watchdock.jar

EXPOSE 8090

ENV WATCHDOCK_PORT=8090
ENV WATCHDOCK_DOCKER_COMMAND=docker

USER watchdock
ENTRYPOINT ["java", "-jar", "/opt/watchdock/watchdock.jar"]
