package com.waleed.naseej.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.waleed.naseej.exception.BadRequestException;
import com.waleed.naseej.exception.ExternalServiceException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Small client for the Claude Messages API, shared by the AI features
 * (business model chat, idea screening, feasibility report...).
 *
 * Every call uses structured outputs: Claude must answer with JSON that matches the
 * schema you pass, and the JSON is mapped straight into your class.
 */
@Slf4j
@Service
public class ClaudeService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    private static final int MAX_TOKENS = 4096;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public ClaudeService(ObjectMapper objectMapper,
                         @Value("${claude.api.key:}") String apiKey,
                         @Value("${claude.model:claude-sonnet-5-5}") String model) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    /**
     * Sends one message to Claude and maps its JSON answer to {@code type}.
     *
     * @param systemPrompt what Claude's job is
     * @param userMessage  the data and the request for this call
     * @param schema       JSON schema the answer must follow (every object needs
     *                     "additionalProperties": false; no minLength/maximum etc.)
     * @param type         class the JSON answer is read into
     */
    public <T> T generateJson(String systemPrompt, String userMessage, Map<String, Object> schema, Class<T> type) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalServiceException("AI service is not configured. Add CLAUDE_API_KEY to the .env file");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", MAX_TOKENS,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userMessage)),
                "output_config", Map.of("format", Map.of("type", "json_schema", "schema", schema)));

        ClaudeResponse response;
        try {
            response = restClient.post()
                    .uri(API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ClaudeResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Claude API returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExternalServiceException("AI service returned an error, please try again later");
        } catch (RestClientException e) {
            log.error("Could not reach Claude API", e);
            throw new ExternalServiceException("Could not reach the AI service, please try again later");
        }

        if (response == null || response.content() == null) {
            throw new ExternalServiceException("AI service returned an empty response");
        }
        if ("refusal".equals(response.stopReason())) {
            throw new BadRequestException("The AI could not process this request, please rephrase it");
        }
        if ("max_tokens".equals(response.stopReason())) {
            throw new ExternalServiceException("The AI response was cut off, please try a shorter request");
        }

        String json = response.content().stream()
                .filter(block -> "text".equals(block.type()) && block.text() != null)
                .map(ContentBlock::text)
                .findFirst()
                .orElseThrow(() -> new ExternalServiceException("AI service returned no answer"));

        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            log.error("Claude returned JSON that does not match {}: {}", type.getSimpleName(), json, e);
            throw new ExternalServiceException("AI service returned an invalid answer, please try again");
        }
    }

    //Turns any object into JSON. handy for putting current data inside a prompt.
    public String toJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ClaudeResponse(List<ContentBlock> content, @JsonProperty("stop_reason") String stopReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ContentBlock(String type, String text) {
    }
}
