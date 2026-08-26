plugins {
	java
	alias(libs.plugins.spring.boot)
	alias(libs.plugins.spring.dependency.management)
}

description = "core-service"

extra["springCloudVersion"] = "2025.1.3"

dependencies {
	implementation(project(":shared-core"))

	// custom starters
	implementation(libs.lisovskyi.security)
	implementation(libs.lisovskyi.jpa)
	implementation(libs.lisovskyi.web.error)

	implementation(libs.spring.actuator)
	implementation(libs.spring.data.jpa)
	implementation(libs.spring.data.redis)
	implementation(libs.spring.flyway)
	implementation(libs.spring.validation)
	implementation(libs.spring.webmvc)
	implementation(libs.flyway.postgresql)
//	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j")
	implementation(libs.mapstruct.core)

	compileOnly(libs.lombok)

	developmentOnly(libs.spring.devtools)

	runtimeOnly(libs.postgresql)

	annotationProcessor(libs.mapstruct.processor)
	annotationProcessor(libs.spring.configuration.processor)
	annotationProcessor(libs.lombok)
	annotationProcessor(libs.lombok.mapstruct.binding)

	testImplementation(libs.spring.actuator.test)
	testImplementation(libs.spring.data.jpa.test)
	testImplementation(libs.spring.flyway.test)
	testImplementation(libs.spring.validation.test)
	testImplementation(libs.spring.webmvc.test)
	testImplementation(libs.spring.boot.testcontainers)
	testImplementation(libs.testcontainers.junit)
	testImplementation(libs.testcontainers.postgresql)
	testRuntimeOnly(libs.junit.platform.launcher)
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

// Ahead-of-time processing for tests tries to generate AOT artifacts for
// TestcontainersConfiguration and fails resolving connection details for
// zipkinContainer (a plain GenericContainer, not a @ServiceConnection - wired
// manually via DynamicPropertyRegistrar instead). Same failure mode shared-core
// already hit and disabled AOT for; scoped to tests only here so a real
// native-image build of the app itself (ProcessAot) is untouched.
tasks.withType<org.springframework.boot.gradle.tasks.aot.ProcessTestAot> {
	enabled = false
}

tasks.register<Exec>("dopplerRun") {
	group = "application"
	description = "Run application with Doppler"

	val gradlewCmd = if (System.getProperty("os.name").lowercase().contains("windows")) {
		"gradlew.bat"
	} else {
		"./gradlew"
	}

	commandLine("doppler", "run", "--", gradlewCmd, "bootRun")
}
