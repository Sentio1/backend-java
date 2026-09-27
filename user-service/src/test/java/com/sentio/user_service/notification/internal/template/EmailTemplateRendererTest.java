package com.sentio.user_service.notification.internal.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Real {@link SpringTemplateEngine} pointed at the actual classpath template (same engine type and
 * resolver setup Spring Boot's Thymeleaf autoconfiguration wires up - the plain, non-Spring {@code
 * TemplateEngine} defaults to the OGNL-based {@code StandardDialect}, which isn't even on this
 * app's classpath since the Spring dialect uses SpEL instead) - a mocked engine would only prove
 * this class calls {@code process()}, not that the real {@code mail/email-verification.html}
 * still renders.
 */
class EmailTemplateRendererTest {

    private static final String CONFIRMATION_URL = "http://localhost:5173/verify-email?token=raw-token";

    private EmailTemplateRenderer renderer;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);

        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        renderer = new EmailTemplateRenderer(templateEngine);
    }

    @Test
    void withFirstName_greetsByName() {
        String html = renderer.renderVerificationEmail("Олена", CONFIRMATION_URL, Instant.now());

        assertThat(html).contains("Вітаємо, Олена.");
    }

    @Test
    void nullFirstName_fallsBackToGenericGreeting() {
        String html = renderer.renderVerificationEmail(null, CONFIRMATION_URL, Instant.now());

        assertThat(html).contains("Вітаємо.");
        assertThat(html).doesNotContain("Вітаємо, null");
    }

    @Test
    void emptyFirstName_fallsBackToGenericGreetingToo() {
        String html = renderer.renderVerificationEmail("", CONFIRMATION_URL, Instant.now());

        assertThat(html).contains("Вітаємо.");
    }

    @Test
    void confirmationUrl_isRenderedAsTheLinkTarget() {
        String html = renderer.renderVerificationEmail("Олена", CONFIRMATION_URL, Instant.now());

        assertThat(html).contains("href=\"" + CONFIRMATION_URL + "\"");
        assertThat(html).contains(">" + CONFIRMATION_URL + "<");
    }

    @Test
    void expiresAt_isFormattedInKyivTimeNotUtc() {
        // 2026-01-15T10:00:00Z is 12:00 in Europe/Kyiv (UTC+2 in January) - if this ever
        // renders as 10:00 again, someone reverted the fix and reintroduced the UTC bug.
        Instant expiresAt = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        String html = renderer.renderVerificationEmail("Олена", CONFIRMATION_URL, expiresAt);

        assertThat(html).contains("15.01.2026 12:00 за київським часом");
        assertThat(html).doesNotContain("UTC");
    }

    @Test
    void nullExpiresAt_rendersEmptyInsteadOfThrowing() {
        String html = renderer.renderVerificationEmail("Олена", CONFIRMATION_URL, null);

        assertThat(html).contains("Посилання діє до");
    }

    private static final String RESET_URL = "http://localhost:5173/reset-password?token=raw-token";

    @Test
    void passwordReset_withFirstName_greetsByName() {
        String html = renderer.renderPasswordResetEmail("Олена", RESET_URL, Instant.now());

        assertThat(html).contains("Вітаємо, Олена.");
    }

    @Test
    void passwordReset_nullFirstName_fallsBackToGenericGreeting() {
        String html = renderer.renderPasswordResetEmail(null, RESET_URL, Instant.now());

        assertThat(html).contains("Вітаємо.");
        assertThat(html).doesNotContain("Вітаємо, null");
    }

    @Test
    void passwordReset_resetUrl_isRenderedAsTheLinkTarget() {
        String html = renderer.renderPasswordResetEmail("Олена", RESET_URL, Instant.now());

        assertThat(html).contains("href=\"" + RESET_URL + "\"");
        assertThat(html).contains(">" + RESET_URL + "<");
    }

    @Test
    void passwordReset_expiresAt_isFormattedInKyivTimeNotUtc() {
        Instant expiresAt = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC).toInstant();

        String html = renderer.renderPasswordResetEmail("Олена", RESET_URL, expiresAt);

        assertThat(html).contains("15.01.2026 12:00 за київським часом");
        assertThat(html).doesNotContain("UTC");
    }

    @Test
    void passwordReset_nullExpiresAt_rendersEmptyInsteadOfThrowing() {
        String html = renderer.renderPasswordResetEmail("Олена", RESET_URL, null);

        assertThat(html).contains("Посилання діє до");
    }
}
