plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    java
}

allprojects {
    group = "com.sentio"
    version = "0.0.1-SNAPSHOT"
    
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

subprojects {
    pluginManager.apply("java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}