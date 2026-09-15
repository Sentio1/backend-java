plugins {
    `java-library`
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "shared-core"

dependencies {
    // Custom Starters
    api(libs.lisovskyi.security)
    api(libs.lisovskyi.jpa)
    api(libs.lisovskyi.web.error)

    // Spring Modulith BOM & APIs (для дотримання модульності в кожному сервісі)
    api(platform(libs.spring.modulith.bom))
    api(libs.spring.modulith.api)
    api(libs.spring.modulith.events.api)
    // Runtime для Modulith AOT hints підтягується як runtime-залежність бібліотеки
    runtimeOnly(libs.spring.modulith.runtime)

    // Core Data & Caching
    api(libs.spring.boot.starter.cache)
    api(libs.spring.data.commons)
    api(libs.spring.data.jpa)
    api(libs.caffeine)

    // Actuator & Validation
    api(libs.spring.actuator)
    api(libs.spring.validation)

    // Observability & Metrics
    api(libs.micrometer.core)
    api(libs.micrometer.prometheus)
    api(libs.micrometer.tracing.bridge.otel)
    api(libs.opentelemetry.exporter.otlp)
    api(libs.loki.logback)

    // Utilities & Functional
    api(libs.jackson.databind.nullable)
    api(libs.aspectjweaver)
    api(libs.vavr)

    compileOnly(libs.spring.webmvc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessAot> {
    enabled = false
}