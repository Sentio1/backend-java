plugins {
    `java-library`
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.sentio"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(libs.spring.boot.starter.cache)
    api(libs.spring.data.commons)
    api(libs.spring.data.jpa)
    api(libs.lisovskyi.web.error)
    api(libs.lisovskyi.security)
    api(libs.caffeine)
    api(libs.jackson.databind.nullable)
    api(libs.aspectjweaver)

    api(libs.micrometer.core)
    api(libs.micrometer.prometheus)

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
