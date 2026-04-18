package com.leadrush.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leadrush.ai.adapter.LLMAdapter;
import com.leadrush.ai.dto.GenerateEmailRequest;
import com.leadrush.ai.dto.GenerateEmailResponse;
import com.leadrush.ai.dto.SubjectSuggestionsRequest;
import com.leadrush.common.exception.BusinessException;
import com.leadrush.common.exception.ResourceNotFoundException;
import com.leadrush.contact.entity.Contact;
import com.leadrush.contact.repository.ContactRepository;
import com.leadrush.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AI Email Writer — generates personalized cold emails and subject-line variants.
 *
 * Strategy: single-turn completion with a tight system prompt that instructs the LLM
 * to return JSON. We parse it, fall back to treating the whole output as the body
 * if the model ignores the JSON instruction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIEmailWriterService {

    private final LLMAdapter llmAdapter;
    private final ContactRepository contactRepository;
    private final ObjectMapper objectMapper;

    // ── Email generation ──

    public GenerateEmailResponse generateEmail(GenerateEmailRequest request) {
        requireReady();

        UUID wsId = TenantContext.getWorkspaceId();
        Contact contact = contactRepository.findByIdAndWorkspaceId(request.getContactId(), wsId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", request.getContactId()));

        String systemPrompt = """
                You are an expert B2B cold email writer. Write short, specific, human-sounding emails.
                Never use corporate buzzwords ("synergize", "leverage", "cutting-edge"). Never invent facts.
                Respond ONLY with a JSON object matching this schema, no markdown:
                {
                  "subject": "...",
                  "bodyHtml": "... (plain HTML, paragraphs wrapped in <p>)",
                  "bodyText": "... (plain text alternative)"
                }
                Keep the subject under 60 characters. Open with something specific to the recipient, not a generic pleasantry.
                Use template variables {{firstName}}, {{companyName}}, {{title}} — do NOT hard-code the person's actual name.
                """;

        String lengthNote = switch (safeUpper(request.getLength())) {
            case "SHORT" -> "Keep the body under 80 words.";
            case "LONG" -> "Aim for 180-250 words with two paragraphs and a clear ask.";
            default -> "Aim for 120-150 words — three short paragraphs max.";
        };

        String toneNote = (request.getTone() != null && !request.getTone().isBlank())
                ? "Tone: " + request.getTone() + "."
                : "Tone: warm, professional, confident.";

        String userPrompt = """
                Write a cold outreach email personalized for this lead.

                Lead:
                  first name: %s
                  title:      %s
                  company:    %s
                  industry:   %s

                Value proposition from the sender:
                  %s

                %s
                %s
                """.formatted(
                orDash(contact.getFirstName()),
                orDash(contact.getTitle()),
                contact.getCompany() != null ? orDash(contact.getCompany().getName()) : "-",
                contact.getCompany() != null ? orDash(contact.getCompany().getIndustry()) : "-",
                request.getValueProp() != null && !request.getValueProp().isBlank()
                        ? request.getValueProp()
                        : "(none — write a curious, open-ended intro asking about their current approach)",
                toneNote,
                lengthNote
        );

        String raw = llmAdapter.complete(new LLMAdapter.CompletionRequest(
                systemPrompt, userPrompt, null, 0.8, 800));

        return parseEmailResponse(raw);
    }

    // ── Subject-line variants ──

    public List<String> suggestSubjects(SubjectSuggestionsRequest request) {
        requireReady();
        int count = Math.min(Math.max(request.getCount() == null ? 5 : request.getCount(), 1), 10);

        String systemPrompt = """
                You write high-open-rate B2B cold email subject lines.
                Rules:
                - Each line under 60 characters
                - Avoid ALL CAPS, emojis, exclamation marks, and spammy phrases ("free", "guarantee", "$$$")
                - Be specific, not generic — hint at curiosity or relevance
                - Return ONLY a JSON array of strings. No prose, no numbering, no markdown.
                """;

        String userPrompt = """
                Generate %d alternative subject lines as a JSON array.

                Original subject: %s
                %s
                """.formatted(
                count,
                request.getSubject(),
                (request.getBodyPreview() != null && !request.getBodyPreview().isBlank())
                        ? "Email context: " + truncate(request.getBodyPreview(), 500)
                        : ""
        );

        String raw = llmAdapter.complete(new LLMAdapter.CompletionRequest(
                systemPrompt, userPrompt, null, 0.9, 500));

        return parseSubjectList(raw, count);
    }

    // ── Helpers ──

    private void requireReady() {
        if (!llmAdapter.isReady()) {
            throw new BusinessException("AI is not configured yet. Set GROQ_API_KEY on the server.");
        }
    }

    private GenerateEmailResponse parseEmailResponse(String raw) {
        String json = extractJson(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            return GenerateEmailResponse.builder()
                    .subject(textOrNull(node, "subject"))
                    .bodyHtml(textOrNull(node, "bodyHtml"))
                    .bodyText(textOrNull(node, "bodyText"))
                    .build();
        } catch (Exception e) {
            // Fallback — treat the whole thing as body text
            log.warn("AI returned non-JSON, using raw as body: {}", e.getMessage());
            return GenerateEmailResponse.builder()
                    .subject("(set a subject)")
                    .bodyHtml("<p>" + raw.replace("\n\n", "</p><p>").replace("\n", "<br>") + "</p>")
                    .bodyText(raw)
                    .build();
        }
    }

    private List<String> parseSubjectList(String raw, int expected) {
        String json = extractJson(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                List<String> out = new ArrayList<>();
                node.forEach(n -> out.add(n.asText()));
                return out;
            }
        } catch (Exception ignored) {
            // Fall through to line split
        }
        // Fallback — split lines, strip bullets/quotes
        List<String> out = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String cleaned = line.replaceAll("^[\\s\\-\\d\\.\"'`]+", "").replaceAll("[\"'`]+$", "").trim();
            if (!cleaned.isEmpty()) out.add(cleaned);
            if (out.size() >= expected) break;
        }
        return out;
    }

    /**
     * Pull the first JSON object or array out of the model's response —
     * tolerant to the model wrapping its output in ```json fences.
     */
    private String extractJson(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int closingFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && closingFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, closingFence).trim();
            }
        }
        int openObj = trimmed.indexOf('{');
        int openArr = trimmed.indexOf('[');
        int start = (openObj >= 0 && (openArr < 0 || openObj < openArr)) ? openObj : openArr;
        if (start < 0) return trimmed;
        int endObj = trimmed.lastIndexOf('}');
        int endArr = trimmed.lastIndexOf(']');
        int end = Math.max(endObj, endArr);
        if (end < start) return trimmed;
        return trimmed.substring(start, end + 1);
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String safeUpper(String value) {
        return value == null ? "" : value.toUpperCase();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
