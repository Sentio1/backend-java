package com.sentio.user_service.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sentio.user_service.TestcontainersConfiguration;
import com.sentio.user_service.user.entity.User;
import com.sentio.user_service.user.enums.PlatformRole;
import com.sentio.user_service.user.repository.UserRepository;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserAdminServiceConcurrencyIT {

    @Autowired
    private UserAdminService userAdminService;

    @Autowired
    private UserRepository userRepository;

    private User admin1;
    private User admin2;

    @BeforeEach
    void setUp() {
        org.springframework.security.core.context.SecurityContext context = 
            org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        org.springframework.security.core.Authentication auth = 
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("admin", "pass", 
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        context.setAuthentication(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);

        // Create exactly two admins
        admin1 = User.builder()
                .email("admin1@sentio.dev")
                .password("password123")
                .platformRole(PlatformRole.ADMIN)
                .build();
        admin1 = userRepository.saveAndFlush(admin1);

        admin2 = User.builder()
                .email("admin2@sentio.dev")
                .password("password123")
                .platformRole(PlatformRole.ADMIN)
                .build();
        admin2 = userRepository.saveAndFlush(admin2);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void demoteFromAdmin_concurrentDemotion_onlyOneSucceedsAndTheOtherFails() throws Exception {
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        org.springframework.security.core.context.SecurityContext context = 
            org.springframework.security.core.context.SecurityContextHolder.getContext();

        Callable<Void> demoteAdmin1 = () -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            bothReady.countDown();
            go.await();
            userAdminService.demoteFromAdmin(admin1.getId());
            return null;
        };

        Callable<Void> demoteAdmin2 = () -> {
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            bothReady.countDown();
            go.await();
            userAdminService.demoteFromAdmin(admin2.getId());
            return null;
        };

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(demoteAdmin1);
            Future<Void> second = executor.submit(demoteAdmin2);

            assertThat(bothReady.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int succeeded = 0;
            int failed = 0;
            for (Future<Void> future : List.of(first, second)) {
                try {
                    future.get(10, TimeUnit.SECONDS);
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
                    failed++;
                }
            }

            // Only one demotion should succeed, leaving exactly one admin.
            // If they both succeed, the test will fail here.
            assertThat(succeeded).isEqualTo(1);
            assertThat(failed).isEqualTo(1);

            long adminCount = userRepository.countByPlatformRole(PlatformRole.ADMIN);
            assertThat(adminCount).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
