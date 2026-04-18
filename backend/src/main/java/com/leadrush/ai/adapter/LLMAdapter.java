package com.leadrush.ai.adapter;

import java.util.List;

// LLM provider adapter. Intentionally narrow: single-turn completion only.
public interface LLMAdapter {

    String providerKey();

    boolean isReady();

    String complete(CompletionRequest request);

    record CompletionRequest(
            String systemPrompt,
            String userPrompt,
            String model,
            double temperature,
            int maxTokens
    ) {
        public static CompletionRequest of(String systemPrompt, String userPrompt) {
            return new CompletionRequest(systemPrompt, userPrompt, null, 0.7, 1024);
        }
    }

    record Variants(List<String> choices) {}
}
