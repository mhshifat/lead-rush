package com.leadrush.ai.adapter.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.leadrush.ai.adapter.LLMAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Groq LLM adapter — calls the OpenAI-compatible Chat Completions endpoint.
 *
 * Docs: https://console.groq.com/docs/api-reference#chat-create
 *
 * API key is read from `groq.api-key` / `GROQ_API_KEY`. If unset, isReady() returns
 * false and complete() throws — the AI service checks isReady() before calling.
 */
@Component
@Slf4j
public class GroqAdapter implements LLMAdapter {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";
    /** Fast, capable default. Groq updates the lineup often — override via config if needed. */
    private static final String DEFAULT_MODEL = "llama-3.3-70b-versatile";

    private final String apiKey;
    private final String defaultModel;
    private final RestClient httpClient;

    public GroqAdapter(
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.model:" + DEFAULT_MODEL + "}") String defaultModel
    ) {
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.httpClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String providerKey() {
        return "groq";
    }

    @Override
    public boolean isReady() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String complete(CompletionRequest request) {
        if (!isReady()) {
            throw new IllegalStateException("Groq API key is not configured. Set GROQ_API_KEY.");
        }

        String model = request.model() != null && !request.model().isBlank()
                ? request.model() : defaultModel;

        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt() == null ? "" : request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())
                ),
                "temperature", request.temperature(),
                "max_tokens", request.maxTokens()
        );

        try {
            ChatCompletionResponse response = httpClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(payload)
                    .retrieve()
                    .body(ChatCompletionResponse.class);

            if (response == null || response.choices() == null || response.choices().isEmpty()) {
                throw new IllegalStateException("Groq returned no choices");
            }
            return response.choices().get(0).message().content();

        } catch (HttpClientErrorException e) {
            log.warn("Groq API error ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("Groq API call failed: " + e.getStatusCode());
        }
    }

    // ── Groq / OpenAI response shape (only the bits we need) ──

    record ChatCompletionResponse(List<Choice> choices) {
        @JsonCreator
        ChatCompletionResponse(@JsonProperty("choices") List<Choice> choices) { this.choices = choices; }
    }

    record Choice(Message message) {
        @JsonCreator
        Choice(@JsonProperty("message") Message message) { this.message = message; }
    }

    record Message(String role, String content) {
        @JsonCreator
        Message(@JsonProperty("role") String role, @JsonProperty("content") String content) {
            this.role = role;
            this.content = content;
        }
    }
}
