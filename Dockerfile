FROM gradle:9-jdk25-alpine AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

ARG MODULE_NAME

COPY . .

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN

RUN --mount=type=cache,target=/home/gradle/.gradle \
    --mount=type=secret,id=gradle_properties,target=/home/gradle/.gradle/gradle.properties,required=false \
    GITHUB_ACTOR=${GITHUB_ACTOR} GITHUB_TOKEN=${GITHUB_TOKEN} gradle :${MODULE_NAME}:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

ARG MODULE_NAME

COPY --from=builder /app/${MODULE_NAME}/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]