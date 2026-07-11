# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY petspark-server/pom.xml petspark-server/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -pl petspark-server -am dependency:go-offline

COPY petspark-server/src petspark-server/src
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw --batch-mode --no-transfer-progress -pl petspark-server -am -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 petspark \
    && useradd --system --uid 10001 --gid petspark --home-dir /app --shell /usr/sbin/nologin petspark

WORKDIR /app
COPY --from=build /workspace/petspark-server/target/petspark-server-*.jar /app/petspark-server.jar
RUN mkdir -p /app/data/files && chown -R petspark:petspark /app

USER petspark
ENV FILE_STORAGE_ROOT=/app/data/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8"
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/petspark-server.jar"]
