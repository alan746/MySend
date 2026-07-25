package com.mysend.account;

import com.mysend.common.ApiException;
import com.mysend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class VerificationMailer {

    private final JavaMailSender mailSender;
    private final AppProperties properties;

    public VerificationMailer(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    public boolean deliver(String email, String code) {
        if (!properties.mailDeliveryEnabled()) {
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(email);
        message.setSubject("Your MySend verification code");
        message.setText("""
                Your MySend verification code is %s.

                It expires in 10 minutes. If you did not request this code,
                you can ignore this message.
                """.formatted(code));
        try {
            mailSender.send(message);
            return true;
        } catch (MailException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "MAIL_UNAVAILABLE",
                    "The verification email could not be sent; please try again"
            );
        }
    }

    public boolean canExposeDevelopmentCode() {
        return !properties.mailDeliveryEnabled() && properties.developmentCodeEnabled();
    }
}
