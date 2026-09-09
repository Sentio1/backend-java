package com.sentio.user_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Without this, RefreshTokenCleanupJob's @Scheduled method is registered but never
// actually fires - Spring Boot doesn't wire up the scheduling infrastructure on its
// own, @EnableScheduling has to be present somewhere.
@EnableScheduling
@SpringBootApplication
/** UserServiceApplication class. */
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
