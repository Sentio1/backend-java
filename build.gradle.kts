plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    java
    id("com.diffplug.spotless") version("8.9.0")
}

allprojects {
    group = "com.sentio"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/Sentio1/backend-java")
            credentials {
                username = findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR") ?: ""
                password = findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN") ?: ""
            }
        }
        mavenLocal()
    }
}

subprojects {
    pluginManager.apply("java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

//    spotless {
//        java {
//            palantirJavaFormat()
//
//            removeUnusedImports()
//            trimTrailingWhitespace()
//            endWithNewline()
//        }
//    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}