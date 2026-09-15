package com.sentio.user_service.identity.user.internal;

import com.sentio.user_service.identity.user.internal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ServiceSecretReader implements CommandLineRunner {

    private static final String SERVICE_EMAIL = "registry-monitor@service.internal";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String @NonNull ... args) {
        String secret = System.getenv("SERVICE_SECRET");
        if (secret == null || secret.isBlank()) {
            log.warn("SERVICE_SECRET не задано в цьому оточенні - пропускаю provisioning registry-monitor");
            return;
        }

        userRepository
                .findByEmail(SERVICE_EMAIL)
                .ifPresentOrElse(
                        user -> {
                            user.setPassword(passwordEncoder.encode(secret));
                            userRepository.save(user);
                        },
                        () -> log.warn("registry-monitor@service.internal не знайдено"));
    }
}
