package com.slotmind.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.slotmind.config.SlotMindProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

@Component
public class GeminiLlmGateway implements LlmGateway {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmGateway.class);

    private final SlotMindProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public GeminiLlmGateway(SlotMindProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /** Calls Gemini generateContent and returns the candidate text. */
    @Override
    public String call(String prompt) {
        HttpResponse<String> response = send(prompt, false, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            throw new LlmException("Gemini returned HTTP " + response.statusCode());
        }
        return readText(response.body());
    }

    /** Streams Gemini SSE lines and passes each text chunk to the consumer. */
    @Override
    public void stream(String prompt, Consumer<String> onChunk) {
        HttpResponse<java.util.stream.Stream<String>> response =
                send(prompt, true, HttpResponse.BodyHandlers.ofLines());
        if (response.statusCode() >= 300) {
            throw new LlmException("Gemini stream returned HTTP " + response.statusCode());
        }
        try (java.util.stream.Stream<String> lines = response.body()) {
            lines.forEach(line -> {
                if (!line.startsWith("data:")) {
                    return;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    return;
                }
                String text = readText(payload);
                if (!text.isEmpty()) {
                    onChunk.accept(text);
                }
            });
        }
    }

    /** Posts the prompt to Gemini and returns the HTTP response. */
    private <T> HttpResponse<T> send(String prompt, boolean stream, HttpResponse.BodyHandler<T> handler) {
        String apiKey = properties.llm().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Gemini API key is not configured");
            throw new LlmException("Gemini API key is not configured");
        }
        String model = properties.llm().model();
        log.debug("Calling Gemini model {} stream {} promptChars {}", model, stream, prompt.length());
        String path = stream ? ":streamGenerateContent?alt=sse" : ":generateContent";
        URI uri = URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + path);
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(20))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(prompt)))
                    .build();
            HttpResponse<T> response = httpClient.send(request, handler);
            if (response.statusCode() >= 300) {
                log.error("Gemini model {} returned HTTP {}", model, response.statusCode());
            } else {
                log.info("Gemini model {} responded HTTP {}", model, response.statusCode());
            }
            return response;
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Gemini call failed for model {}", model, exception);
            throw new LlmException("Gemini call failed", exception);
        }
    }

    private String body(String prompt) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("contents")
                .addObject()
                .putArray("parts")
                .addObject()
                .put("text", prompt);
        root.putObject("generationConfig")
                .put("responseMimeType", "application/json")
                .put("temperature", 0.2);
        return objectMapper.writeValueAsString(root);
    }

    /** Reads the candidate text out of a Gemini JSON payload. */
    private String readText(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new LlmException("Gemini response had no text");
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                text.append(part.path("text").asText(""));
            }
            if (text.isEmpty()) {
                throw new LlmException("Gemini response had no text");
            }
            return text.toString();
        } catch (LlmException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmException("Could not read Gemini response", exception);
        }
    }
}
