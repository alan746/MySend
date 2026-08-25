package com.mysend.account;

import com.mysend.common.ApiException;
import com.mysend.config.AppProperties;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

@Service
public class VerificationMailer {

    private final RestClient resendClient;
    private final AppProperties properties;

    public VerificationMailer(
            @Qualifier("resendRestClient") RestClient resendClient,
            AppProperties properties
    ) {
        this.resendClient = resendClient;
        this.properties = properties;
    }

    public boolean deliver(String email, String code) {
        return deliverMessage(
                email,
                SecurityEmailTemplate.accountVerification(
                        code,
                        properties.appBaseUrl()
                )
        );
    }

    public boolean deliverPasswordCode(String email, String code) {
        return deliverMessage(
                email,
                SecurityEmailTemplate.passwordChange(
                        code,
                        properties.appBaseUrl()
                )
        );
    }

    private boolean deliverMessage(
            String email,
            SecurityEmailTemplate.Message message
    ) {
        if (!properties.mailDeliveryEnabled()) {
            return false;
        }
        try {
            resendClient.post()
                    .uri("/emails")
                    .body(new ResendEmailRequest(
                            properties.mailFrom(),
                            List.of(email),
                            message.subject(),
                            message.html(),
                            message.text()
                    ))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
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

    record ResendEmailRequest(
            String from,
            List<String> to,
            String subject,
            String html,
            String text
    ) {
    }
}
