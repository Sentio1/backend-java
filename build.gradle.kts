plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    java
    id("com.diffplug.spotless") version("8.9.0")
    id("org.graalvm.buildtools.native") version("0.10.2") apply false
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
    pluginManager.apply("checkstyle")
    pluginManager.apply("com.diffplug.spotless")
    pluginManager.apply("org.graalvm.buildtools.native")

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.17.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = true
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    spotless {
        java {
            palantirJavaFormat()

            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    extensions.configure<org.graalvm.buildtools.gradle.dsl.GraalVMExtension> {
        binaries {
            named("main") {
                // Обмежую пам'ять до 12 ГБ
                buildArgs.add("-J-Xmx12G")

                // Обмежую кількість ядер процесора
                val numberOfProcessors = Runtime.getRuntime().availableProcessors() / 2
                buildArgs.add("-J-XX:ActiveProcessorCount=$numberOfProcessors")
            }
        }
    }
}