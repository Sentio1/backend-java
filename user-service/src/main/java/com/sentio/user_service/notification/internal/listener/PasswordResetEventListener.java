package com.sentio.user_service.notification.internal.listener;

import com.sentio.user_service.identity.event.PasswordResetRequestedEvent;
import com.sentio.user_service.notification.internal.mail.EmailSender;
import com.sentio.user_service.notification.internal.template.EmailTemplateRenderer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
@RequiredArgsConstructor
public class PasswordResetEventListener {

    private static final String PASSWORD_RESET_MESSAGE = "Скидання пароля";

    private final EmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;

    @ApplicationModuleListener
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        log.info("Handling password reset request for userId={}, email={}", event.userId(), event.email());

        String confirmationUrl = UriComponentsBuilder.fromUriString(event.resetUrl())
                .queryParam("token", event.rawToken())
                .build()
                .toUriString();

        String htmlBody = templateRenderer.renderPasswordResetEmail(event.firstName(), confirmationUrl, event.expiresAt(), event.userZone());

        emailSender.sendEmail(event.userId(), event.email(), PASSWORD_RESET_MESSAGE, htmlBody);
    }
}
