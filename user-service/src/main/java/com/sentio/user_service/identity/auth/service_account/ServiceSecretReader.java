package com.sentio.user_service.identity.auth.service_account;

import com.sentio.user_service.identity.user.api.dto.UserDto;
import com.sentio.user_service.identity.user.api.service.UserAccountService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Provisions the Registry Monitor service account's secret from the environment (SERVICE_SECRET,
 * Doppler/env) into auth.users.password_hash on startup - the migration (V9) seeds the row with a
 * NULL hash on purpose, so the secret never lands in git even hashed. Until this runs with a
 * secret, the account simply can't get a token (a NULL hash never matches).
 */
@Component
@Slf4j
public class ServiceSecretReader implements CommandLineRunner {

    static final String SERVICE_EMAIL = "registry-monitor@service.internal";

    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final String secret;

    public ServiceSecretReader(
            UserAccountService userAccountService,
            PasswordEncoder passwordEncoder,
            @Value("${SERVICE_SECRET:}") String secret) {
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.secret = secret;
    }

    @Override
    public void run(String @NonNull ... args) {
        if (!StringUtils.hasText(secret)) {
            log.warn("SERVICE_SECRET не задано в цьому оточенні - {} не зможе отримати service token", SERVICE_EMAIL);
            return;
        }

        userAccountService.findActiveByEmail(SERVICE_EMAIL)
                .ifPresentOrElse(this::provision, () -> log.warn("{} не знайдено", SERVICE_EMAIL));
    }

    private void provision(UserDto serviceUser) {
        // Re-hashing on every start would needlessly rewrite the row (a fresh salt
        // means a different hash each time) - only write when the secret changed.
        if (serviceUser.password() != null && passwordEncoder.matches(secret, serviceUser.password())) {
            log.debug("{} secret is up to date", SERVICE_EMAIL);
            return;
        }
        userAccountService.updatePasswordHash(serviceUser.id(), passwordEncoder.encode(secret));
        log.info("{} secret provisioned", SERVICE_EMAIL);
    }
}
