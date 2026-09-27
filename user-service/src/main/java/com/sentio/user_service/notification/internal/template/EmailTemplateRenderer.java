package com.sentio.user_service.notification.internal.template;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Component
public class EmailTemplateRenderer {

    private static final String EMAIL_VERIFICATION_PATH = "mail/email-verification";
    private static final String PASSWORD_RESET_PATH = "mail/password-reset";

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Kyiv");
    private static final DateTimeFormatter BASE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final TemplateEngine templateEngine;

    public EmailTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String renderVerificationEmail(String firstName, String confirmationUrl, Instant expiresAt, ZoneId userZone) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", firstName);
        variables.put("confirmationUrl", confirmationUrl);
        variables.put("expiresAt", formatExpiresAt(expiresAt, userZone));

        return render(EMAIL_VERIFICATION_PATH, variables);
    }

    public String renderPasswordResetEmail(String firstName, String resetUrl, Instant expiresAt, ZoneId userZone) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("firstName", firstName);
        variables.put("resetUrl", resetUrl);
        variables.put("confirmationUrl", resetUrl);
        variables.put("expiresAt", formatExpiresAt(expiresAt, userZone));

        return render(PASSWORD_RESET_PATH, variables);
    }

    private String render(String templatePath, Map<String, Object> variables) {
        Context context = new Context();
        context.setVariables(variables);
        return templateEngine.process(templatePath, context);
    }

    private String formatExpiresAt(Instant expiresAt, ZoneId userZone) {
        if (expiresAt == null) {
            return "";
        }

        ZoneId zone = (userZone != null) ? userZone : DEFAULT_ZONE;
        String formattedTime = BASE_FORMATTER.withZone(zone).format(expiresAt);

        return zone.equals(DEFAULT_ZONE)
                ? formattedTime + " за київським часом"
                : formattedTime + " (" + zone.getId() + ")";
    }
}
