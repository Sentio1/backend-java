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
        context.setVariable("expiresAt", expiresAt != null ? FORMATTER.format(expiresAt) + " за київським часом" : "");

        return templateEngine.process("mail/email-verification", context);
    }
}
