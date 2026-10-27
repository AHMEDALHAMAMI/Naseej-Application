package com.waleed.naseej.service;

import com.waleed.naseej.enums.OtpPurpose;
import com.waleed.naseej.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Calls n8n webhooks. n8n then sends the email from the platform's Gmail account
 * (the OTP workflow is in n8n/naseej-otp-email.workflow.json).
 * When N8N_WEBHOOK_SECRET is set, every call carries it in the X-Naseej-Secret header, so the webhooks
 * (Header Auth in n8n) only accept calls from this backend.
 * Other features (idea completed, contracts) can add their own methods here.
 */
@Slf4j
@Service
public class N8nService {

    public static final String SECRET_HEADER = "X-Naseej-Secret";

    private final RestClient restClient = RestClient.create();
    private final String otpWebhookUrl;
    private final String eventsWebhookUrl;
    private final String webhookSecret;

    public N8nService(@Value("${n8n.webhook.otp-url:}") String otpWebhookUrl,
                      @Value("${n8n.webhook.events-url:}") String eventsWebhookUrl,
                      @Value("${n8n.webhook.secret:}") String webhookSecret) {
        this.otpWebhookUrl = otpWebhookUrl;
        this.eventsWebhookUrl = eventsWebhookUrl;
        this.webhookSecret = webhookSecret;
    }

    // Adds the shared secret when one is configured
    private void secret(HttpHeaders headers) {
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            headers.set(SECRET_HEADER, webhookSecret);
        }
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
                    .headers(this::secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.error("Failed to call n8n OTP webhook: {}", e.getMessage());
            throw new BadRequestException("Could not send the verification code, please try again");
        }
    }

    /**
     * Platform events for the n8n workflows (WhatsApp and email alerts): IDEA_COMPLETED, CONTRACT_ACTIVE,
     * CONTRACT_EXPIRED, PAYMENT_REFUNDED. n8n switches on "event".
     * An alert must never break the action that caused it, so failures are only logged.
     */
    public void sendEvent(String event, Map<String, Object> data) {
        // LinkedHashMap because Map.of rejects null values
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("event", event);
        body.put("occurredAt", LocalDateTime.now().toString());
        body.put("data", data);

        if (eventsWebhookUrl == null || eventsWebhookUrl.isBlank()) {
            log.info("[DEV] N8N_EVENTS_WEBHOOK_URL is empty. Event {}: {}", event, data);
            return;
        }
        try {
            restClient.post()
                    .uri(eventsWebhookUrl)
                    .headers(this::secret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to send n8n event {}: {}", event, e.getMessage());
        }
    }
}