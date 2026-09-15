plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

description = "core-service"

dependencies {
    implementation(project(":shared-core"))

    // Service-specific infrastructure & web
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.data.redis)
    implementation(libs.spring.kafka)

    // Persistence & Migrations
    implementation(libs.spring.flyway)
    implementation(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    compileOnly(libs.lombok)

    // Mapping & Utilities
    implementation(libs.mapstruct.core)
    developmentOnly(libs.spring.devtools)

    // Annotation processors are applied to every subproject via the root
    // build.gradle.kts (annotation-processors / test-annotation-processors
    // bundles) - no need to redeclare mapstruct/lombok processors here.

    // Testing (Modulith test and core test starters are inherited via shared-core where applicable)
    testImplementation(libs.spring.actuator.test)
    testImplementation(libs.spring.data.jpa.test)
    testImplementation(libs.spring.flyway.test)
    testImplementation(libs.spring.validation.test)
    testImplementation(libs.spring.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.bom.get().toString())
    }
}
