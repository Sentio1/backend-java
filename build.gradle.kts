plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    java
    id("org.graalvm.buildtools.native") version("0.10.5") apply false
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

    extensions.configure<CheckstyleExtension> {
        toolVersion = "10.17.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = true
    }

    tasks.withType<Checkstyle>().configureEach {
        exclude("**/generated/**")
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    plugins.withId("org.springframework.boot") {
        // graalvm native image
        pluginManager.apply("org.graalvm.buildtools.native")
        extensions.configure<org.graalvm.buildtools.gradle.dsl.GraalVMExtension> {
            binaries {
                named("main") {
                    // Обмежую пам'ять до половини оперативної пам'яті (50%)
                    buildArgs.add("-J-XX:MaxRAMPercentage=50.0")

                    // Обмежую кількість ядер процесора
                    val numberOfProcessors = Runtime.getRuntime().availableProcessors() / 2
                    val activeProcessors = if (numberOfProcessors > 0) numberOfProcessors else 1
                    buildArgs.add("-J-XX:ActiveProcessorCount=$activeProcessors")
                }
            }
        }
        
        tasks.matching { it.name == "collectReachabilityMetadata" }.configureEach {
            enabled = false
        }

        // Doppler run
        tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
            doFirst {
                val output = providers.exec {
                    commandLine("doppler", "secrets", "download", "--no-file", "--format", "env")
                }.standardOutput.asText.get()

                output.lines()
                    .filter { it.contains("=") }
                    .forEach { line ->
                        val (key, value) = line.split("=", limit = 2)
                        environment(key, value.trim('"'))
                    }
            }
        }
    }
}
