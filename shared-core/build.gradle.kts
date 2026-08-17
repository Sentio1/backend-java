plugins {
    `java-library`
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

tasks.bootJar {
    enabled = false
}

tasks.jar {
    enabled = true
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
    api(libs.caffeine)

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Вимикаємо створення виконуваного Spring Boot архіву
tasks.bootJar {
    enabled = false
}

// Вмикаємо створення звичайного JAR для імпорту в інші модулі
tasks.jar {
    enabled = true
}

// Вимикаємо AOT (Ahead-of-Time) компіляцію, на якій падає збірка
tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessAot> {
    enabled = false
}

tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessTestAot> {
    enabled = false
}
