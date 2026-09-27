package com.sentio.user_service.notification.internal.listener;

import com.sentio.user_service.identity.event.EmailVerificationRequestedEvent;
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
public class EmailVerificationEventListener {

    private static final String VERIFICATION_MESSAGE = "Підтвердження електронної пошти";

    private final EmailSender emailSender;
    private final EmailTemplateRenderer templateRenderer;

    @ApplicationModuleListener
    public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
        log.info("Received EmailVerificationRequested event for userId={}", event.userId());

        String confirmationUrl = UriComponentsBuilder.fromUriString(event.verificationUrl())
                .queryParam("token", event.rawToken())
                .build()
                .toUriString();

        String htmlBody = templateRenderer.renderVerificationEmail(event.firstName(), confirmationUrl, event.expiresAt(), event.userZone());

        emailSender.sendEmail(event.userId(), event.email(), VERIFICATION_MESSAGE, htmlBody);
    }
}
