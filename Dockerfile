FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew ./
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --version

RUN ./gradlew --no-daemon dependencies > /dev/null || true

COPY src src

RUN ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -u 10001 -r -s /bin/false appuser
USER appuser

ENV TZ=Europe/Warsaw

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -XX:+ExitOnOutOfMemoryError"

ENV SPRING_PROFILES_ACTIVE=docker

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=20s --retries=10 \
  CMD wget -qO- http://127.0.0.1:8080/ping || exit 1

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]