package com.sentio.user_service.notification.internal.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailSenderTest {

    private static final Long USER_ID = 42L;
    private static final String FROM = "no-reply@sentio.dev";

    @Mock
    private JavaMailSender mailSender;

    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        emailSender = new EmailSender(mailSender);
        ReflectionTestUtils.setField(emailSender, "fromAddress", FROM);
    }

    private static MimeMessage realMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void validMessage_isHandedToTheMailSender() {
        MimeMessage message = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailSender.sendEmail(USER_ID, "user@sentio.dev", "Підтвердження", "<p>hello</p>");

        verify(mailSender).send(message);
    }

    @Test
    void mailSenderFailure_isWrappedAsIllegalStateException_notLeakedAsCheckedException() {
        MimeMessage message = realMimeMessage();
        when(mailSender.createMimeMessage()).thenReturn(message);

        // Two "@" makes the domain part start with "@", which is invalid under RFC 822
        // grammar even in JavaMail's lenient (non-strict) parsing mode - MimeMessageHelper's
        // internal InternetAddress.parse throws a checked AddressException for it, exercising
        // the catch block for real instead of mocking a throw that could drift from what
        // JavaMail actually does.
        assertThatThrownBy(() -> emailSender.sendEmail(USER_ID, "broken@@sentio.dev", "Subject", "<p>hi</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(MessagingException.class);
    }
}
