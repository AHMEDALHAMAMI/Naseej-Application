package com.waleed.naseej.service;

import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Calls n8n webhooks. n8n then sends the email from the platform's Gmail account.
 * Other features (idea completed, contracts) can add their own methods here.
 */
@Slf4j
@Service
public class N8nService {

    private final RestClient restClient = RestClient.create();
    private final String otpWebhookUrl;

    public N8nService(@Value("${n8n.webhook.otp-url:}") String otpWebhookUrl) {
        this.otpWebhookUrl = otpWebhookUrl;
    }

    public void sendOtpEmail(String email, String fullName, String code, OtpPurpose purpose, long expiresInMinutes) {
        // Development mode: no webhook configured, print the code instead of emailing it
        if (otpWebhookUrl == null || otpWebhookUrl.isBlank()) {
            log.warn("[DEV] N8N_OTP_WEBHOOK_URL is empty. OTP for {} ({}): {}", email, purpose, code);
            return;
        }

        Map<String, Object> body = Map.of(
                "email", email,
                "fullName", fullName,
                "code", code,
                "purpose", purpose.name(),
                "expiresInMinutes", expiresInMinutes
        );

        try {
            restClient.post()
                    .uri(otpWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to call n8n OTP webhook: {}", e.getMessage());
            throw new BadRequestException("Could not send the verification code, please try again");
        }
    }
}