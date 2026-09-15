plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "user-service"

dependencies {
    // Спільне ядро з усіма стартерами, метриками та Modulith API
    implementation(project(":shared-core"))

    // Web & HTTP
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.oauth2.client)
    implementation(libs.resilience4j.spring.boot)

    // Persistence & Migrations (специфіка user-service)
    implementation(libs.spring.jdbc)
    implementation(libs.spring.data.jdbc)
    implementation(libs.spring.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)

    // Mapping & Utilities
    implementation(libs.mapstruct.core)
    compileOnly(libs.lombok)
    developmentOnly(libs.spring.devtools)

    // Annotation processors are applied to every subproject via the root
    // build.gradle.kts (annotation-processors / test-annotation-processors
    // bundles) - no need to redeclare mapstruct/lombok processors here.

    // Testing
    testImplementation(libs.spring.actuator.test)
    testImplementation(libs.spring.data.jdbc.test)
    testImplementation(libs.spring.data.jpa.test)
    testImplementation(libs.spring.flyway.test)
    testImplementation(libs.spring.jdbc.test)
    testImplementation(libs.spring.validation.test)
    testImplementation(libs.spring.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.spring.modulith.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}
