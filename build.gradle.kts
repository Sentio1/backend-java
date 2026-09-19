plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.graalvm.native.buildtools) apply false
    java
}

allprojects {
    group = "com.sentio"
    version = "0.0.1"

    repositories {
        mavenCentral()
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
        if (name.contains("Aot")) {
            enabled = false
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        "annotationProcessor"(rootProject.libs.bundles.annotation.processors)
        "testAnnotationProcessor"(rootProject.libs.bundles.test.annotation.processors)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-parameters",
                "-Amapstruct.defaultComponentModel=spring",
                "-Amapstruct.unmappedTargetPolicy=ERROR"
            )
        )
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
    }
}
