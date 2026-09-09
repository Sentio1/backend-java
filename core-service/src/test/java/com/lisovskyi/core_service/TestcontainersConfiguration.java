package com.lisovskyi.core_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection(name = "kafka")
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));
    }

    @Bean
    public GenericContainer<?> zipkinContainer() {
        return new GenericContainer<>(DockerImageName.parse("openzipkin/zipkin:latest"))
                .withExposedPorts(9411)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200));
    }

    @Bean
    public DynamicPropertyRegistrar zipkinProperties(GenericContainer<?> zipkinContainer) {
        return registry -> registry.add(
                "management.zipkin.tracing.endpoint",
                () -> "http://" + zipkinContainer.getHost() + ":" + zipkinContainer.getMappedPort(9411)
                        + "/api/v2/spans");
    }
}
