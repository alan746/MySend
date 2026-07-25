FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system mysend \
    && useradd --system --gid mysend --home-dir /app mysend \
    && mkdir -p /app/data /app/uploads \
    && chown -R mysend:mysend /app

COPY --from=build /workspace/target/mysend-api-0.1.0.jar /app/mysend-api.jar

USER mysend
EXPOSE 8080
VOLUME ["/app/uploads"]

ENV SERVER_PORT=8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/mysend-api.jar"]
