package com.sentio.user_service;

import org.springframework.boot.SpringApplication;

/** TestUserServiceApplication class. */
public class TestUserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.from(UserServiceApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
