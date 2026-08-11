FROM gradle:9-jdk25-alpine AS builder
WORKDIR /app
COPY . .
ARG MODULE_NAME
RUN ./gradlew :${MODULE_NAME}:bootJar --no-daemon

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
ARG MODULE_NAME
COPY --from=builder /app/${MODULE_NAME}/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT [ "java", "-jar", "app.jar" ]