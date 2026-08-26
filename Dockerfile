# 1. Base stage with GraalVM (used by both)
FROM ghcr.io/graalvm/native-image-community:25 AS base
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
ARG MODULE_NAME
COPY . .

# 2. Build JVM
FROM base AS build-jvm
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=gradle_properties,target=/root/.gradle/gradle.properties,required=true \
    ./gradlew :${MODULE_NAME}:bootJar --no-daemon

# 3. Build Native
FROM base AS build-native
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=secret,id=gradle_properties,target=/root/.gradle/gradle.properties,required=true \
    ./gradlew :${MODULE_NAME}:nativeCompile --no-daemon

# 4. Final JVM Image (Fast build for local dev)
FROM eclipse-temurin:25-jre-alpine AS jvm
WORKDIR /app
ARG MODULE_NAME
COPY --from=build-jvm /app/${MODULE_NAME}/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]

# 5. Final Native Image (For Production)
FROM debian:bookworm-slim AS native
WORKDIR /app
ARG MODULE_NAME
COPY --from=build-native /app/${MODULE_NAME}/build/native/nativeCompile/${MODULE_NAME} app
EXPOSE 8080
ENTRYPOINT [ "./app" ]