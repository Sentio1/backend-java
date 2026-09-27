package com.sentio.user_service.notification.internal.template;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class EmailTemplateRenderer {

    private static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(KYIV);

    private final TemplateEngine templateEngine;

    public String renderVerificationEmail(String firstName, String confirmationUrl, Instant expiresAt) {
        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("confirmationUrl", confirmationUrl);
        context.setVariable("expiresAt", formatExpiresAt(expiresAt));

        return templateEngine.process("mail/email-verification", context);
    }

    public String renderPasswordResetEmail(String firstName, String resetUrl, Instant expiresAt) {
        Context context = new Context();
        context.setVariable("firstName", firstName);
        context.setVariable("resetUrl", resetUrl);
        // Залишаємо також confirmationUrl на випадок, якщо шаблон використовує таку ж назву змінної
        context.setVariable("confirmationUrl", resetUrl);
        context.setVariable("expiresAt", formatExpiresAt(expiresAt));

        return templateEngine.process("mail/password-reset", context);
    }

    private String formatExpiresAt(Instant expiresAt) {
        return expiresAt != null ? FORMATTER.format(expiresAt) + " за київським часом" : "";
    }
}
