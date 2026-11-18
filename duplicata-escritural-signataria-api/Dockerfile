FROM eclipse-temurin:21.0.13_9-jdk-noble@sha256:06c1f157dacf6b68198bd9a4186dd4c34ef24e65d7bbbe2a0cf8e62571ad38c9 AS build
WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle/wrapper/ gradle/wrapper/
COPY duplicata-escritural-financiamento-api/build.gradle.kts duplicata-escritural-financiamento-api/
COPY duplicata-escritural-signataria-api/build.gradle.kts duplicata-escritural-signataria-api/
COPY duplicata-escritural-signataria-api/src/main/ duplicata-escritural-signataria-api/src/main/
RUN sed -i 's/\r$//' gradlew \
    && sh ./gradlew :duplicata-escritural-signataria-api:bootJar --no-daemon --console=plain \
    && cp duplicata-escritural-signataria-api/build/libs/*.jar /app.jar

FROM eclipse-temurin:21.0.13_9-jre-noble@sha256:0d57a8e90c2bb844c5a8e1b2b71e11eff06dad7edcfd803cd21ed4ae203b2515
RUN groupadd --gid 10001 app \
    && useradd --no-log-init --uid 10001 --gid 10001 --no-create-home --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chmod=0444 /app.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
