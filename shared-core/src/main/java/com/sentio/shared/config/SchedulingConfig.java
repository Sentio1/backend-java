//package com.sentio.shared.config;
//
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.scheduling.annotation.EnableScheduling;
//
//@Configuration
//@EnableScheduling
//@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
//public class SchedulingConfig {
//
//    @Bean
//    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
//        return new RedisLockProvider(connectionFactory, "match-point-backend");
//    }
//}
