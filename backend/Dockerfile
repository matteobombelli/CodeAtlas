FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend
COPY demo-app/build.gradle.kts ./demo-app/build.gradle.kts
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :backend:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S sbsa && adduser -S sbsa -G sbsa
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/backend-*.jar app.jar
USER sbsa
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
