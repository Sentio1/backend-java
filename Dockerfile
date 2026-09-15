# 1. Base: wrapper + build files first, so a source-only change doesn't invalidate
#    the dependency-resolution cache layer below. Every module's build.gradle.kts is
#    needed even though only ${MODULE_NAME} gets built - Gradle's configuration phase
#    evaluates the whole multi-project build declared in settings.gradle.kts.
FROM eclipse-temurin:25-jdk-alpine AS base
WORKDIR /app

ARG BUILD_WORKERS=8
ARG MODULE_NAME
ENV JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=${BUILD_WORKERS} -XX:+UseParallelGC -Xmx16g"
ENV GRADLE_OPTS="-Dorg.gradle.workers.max=${BUILD_WORKERS}"

COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
COPY shared-core/build.gradle.kts shared-core/build.gradle.kts
COPY user-service/build.gradle.kts user-service/build.gradle.kts
COPY core-service/build.gradle.kts core-service/build.gradle.kts
RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${MODULE_NAME}:dependencies --no-daemon || true

COPY shared-core/src shared-core/src
COPY user-service/src user-service/src
COPY core-service/src core-service/src

# 2. Build JVM: bootJar, then explode it into layers (deps / snapshot-deps / loader /
#    application) so the final image below can COPY them as separate, cacheable layers.
FROM base AS jvm-builder
ARG MODULE_NAME

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${MODULE_NAME}:bootJar --no-daemon -x test

WORKDIR /app/${MODULE_NAME}/build/libs
RUN java -Djarmode=tools -jar $(ls *.jar | grep -v 'plain' | head -n 1) \
    extract --layers --destination /workspace/extracted

# 3. Final JVM image (fast build for local dev/test) - non-root, layered, container-aware heap.
FROM eclipse-temurin:25-jre-alpine AS runner-jvm
WORKDIR /application
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=jvm-builder /workspace/extracted/dependencies/ ./
COPY --from=jvm-builder /workspace/extracted/spring-boot-loader/ ./
COPY --from=jvm-builder /workspace/extracted/snapshot-dependencies/ ./
COPY --from=jvm-builder /workspace/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]

# 4. Build Native: fresh GraalVM base (not derived from `base`), so re-declare the args.
FROM ghcr.io/graalvm/native-image-community:25 AS native-builder
WORKDIR /app

ARG BUILD_WORKERS=8
ARG MODULE_NAME
ENV JAVA_TOOL_OPTIONS="-XX:ActiveProcessorCount=${BUILD_WORKERS} -Xmx16g"
ENV GRADLE_OPTS="-Dorg.gradle.workers.max=${BUILD_WORKERS}"

COPY --from=base /app ./
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${MODULE_NAME}:nativeCompile --no-daemon -x test \
    -Pgraalvm.buildArgs="--parallelism=${BUILD_WORKERS}"

# 5. Final native image (for production) - non-root, minimal runtime libs for the binary.
FROM alpine:3.24 AS runner-native
WORKDIR /application

RUN apk add --no-cache libstdc++ gcompat libc6-compat && \
    addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser:appgroup

ARG MODULE_NAME
COPY --from=native-builder /app/${MODULE_NAME}/build/native/nativeCompile/${MODULE_NAME} app

EXPOSE 8080
ENTRYPOINT ["./app"]
