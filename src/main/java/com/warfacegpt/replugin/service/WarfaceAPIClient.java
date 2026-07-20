package com.warfacegpt.replugin.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import ghidra.util.Msg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * API client for WarfaceGPT gateway — hardcoded to gateway.warfacegpt.army.
 * Uses OpenAI-compatible /v1/chat/completions endpoint.
 * Users only see Warface model aliases (warface-scout, warface-core, etc.).
 */
public class WarfaceAPIClient {

    // === Hardcoded WarfaceGPT endpoint — no configuration needed ===
    private static final String WARFACE_API_URL = "https://gateway.warfacegpt.army/v1/chat/completions";
    private static final String WARFACE_MODELS_URL = "https://gateway.warfacegpt.army/v1/models";

    // Warface model aliases — users select from these, never raw provider IDs
    public static final String MODEL_SCOUT = "warface-scout";      // DeepSeek V4 Flash — fast, cheap
    public static final String MODEL_CORE = "warface-core";        // DeepSeek V4 Pro — balanced
    public static final String MODEL_ELITE = "warface-elite";      // DeepSeek V4 Flash — fast analysis
    public static final String MODEL_TITAN = "warface-titan";       // Hermes 4 405B — top reasoning
    public static final String MODEL_VISION = "warface-vision";     // Gemini 3.1 Pro — multimodal
    public static final String MODEL_SUPREME = "warface-supreme";   // Claude Fable 5 — heavyweight
    public static final String MODEL_ARSENAL = "warface-arsenal";   // DeepSeek V4 Pro — exploit dev

    public static final String[] ALL_MODELS = {
        MODEL_SCOUT, MODEL_CORE, MODEL_ELITE, MODEL_TITAN,
        MODEL_VISION, MODEL_SUPREME, MODEL_ARSENAL
    };

    // Recommended models per task
    public static final String DEFAULT_MODEL = MODEL_CORE;
    public static final String RECOMMENDED_VULN = MODEL_CORE;      // Vulnerability detection
    public static final String RECOMMENDED_RENAME = MODEL_SCOUT;    // Fast rename/retype
    public static final String RECOMMENDED_EXPLOIT = MODEL_ARSENAL; // Exploit chain analysis
    public static final String RECOMMENDED_DEEP = MODEL_TITAN;      // Deep binary analysis

    public static final int DEFAULT_TIMEOUT_SECONDS = 120; // RE tasks can be slow
    public static final int DEFAULT_MAX_TOKENS = 8000;
    public static final double DEFAULT_TEMPERATURE = 0.1;

    private OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String apiKey;
    private String model = DEFAULT_MODEL;
    private int maxTokens = DEFAULT_MAX_TOKENS;
    private double temperature = DEFAULT_TEMPERATURE;
    private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

    // System prompt prepended to every request
    private String systemPrompt = WarfacePrompts.SYSTEM_PROMPT;

    public WarfaceAPIClient() {
        this(DEFAULTTimeoutSeconds());
    }

    private static int defaultTimeoutSeconds() {
        return DEFAULT_TIMEOUT_SECONDS;
    }

    public WarfaceAPIClient(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // === Setters ===

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        rebuildHttpClient();
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    // === Getters ===

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public int getMaxTokens() { return maxTokens; }
    public double getTemperature() { return temperature; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public String getSystemPrompt() { return systemPrompt; }

    private void rebuildHttpClient() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
    }

    // === Core API Call ===

    /**
     * Send a prompt to the WarfaceGPT gateway (streaming)
     */
    public String sendRequest(String prompt) throws IOException {
        return sendRequest(prompt, new DefaultStreamCallback());
    }

    /**
     * Send a prompt to the WarfaceGPT gateway with streaming callback
     */
    public String sendRequest(String prompt, StreamCallback callback) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "WarfaceGPT API key not configured. Get one at https://warfacegpt.army");
        }

        OpenAIRequest request = new OpenAIRequest();
        request.model = model;
        request.messages = List.of(
            new OpenAIMessage("system", systemPrompt),
            new OpenAIMessage("user", prompt)
        );
        request.maxTokens = maxTokens;
        request.temperature = temperature;
        request.stream = true;

        String jsonRequest = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
                .url(WARFACE_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8")))
                .build();

        return processStream(httpRequest, callback);
    }

    /**
     * Send a request with a custom system prompt (for different analysis modes)
     */
    public String sendRequestWithSystemPrompt(String userPrompt, String customSystemPrompt, StreamCallback callback) throws IOException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "WarfaceGPT API key not configured. Get one at https://warfacegpt.army");
        }

        OpenAIRequest request = new OpenAIRequest();
        request.model = model;
        request.messages = List.of(
            new OpenAIMessage("system", customSystemPrompt),
            new OpenAIMessage("user", userPrompt)
        );
        request.maxTokens = maxTokens;
        request.temperature = temperature;
        request.stream = true;

        String jsonRequest = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
                .url(WARFACE_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .post(RequestBody.create(jsonRequest, MediaType.get("application/json; charset=utf-8")))
                .build();

        return processStream(httpRequest, callback);
    }

    /**
     * Fetch available models from the WarfaceGPT gateway
     */
    public List<String> fetchAvailableModels() throws IOException {
        Request httpRequest = new Request.Builder()
                .url(WARFACE_MODELS_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Failed to fetch models: " + response.code() + " " + response.message());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            try {
                ModelsResponse modelsResponse = objectMapper.readValue(responseBody, ModelsResponse.class);
                if (modelsResponse.data != null) {
                    return modelsResponse.data.stream()
                        .map(m -> m.id)
                        .filter(id -> id.startsWith("warface-"))
                        .sorted()
                        .collect(java.util.stream.Collectors.toList());
                }
            } catch (Exception e) {
                Msg.warn(this, "Failed to parse models response: " + e.getMessage());
            }
            return List.of(ALL_MODELS);
        }
    }

    // === Streaming ===

    public interface StreamCallback {
        void onPartialResponse(String partialContent);
        void onComplete(String fullContent);
        void onError(Exception error);
    }

    private static class DefaultStreamCallback implements StreamCallback {
        @Override
        public void onPartialResponse(String partialContent) { }
        @Override
        public void onComplete(String fullContent) { }
        @Override
        public void onError(Exception error) { }
    }

    private String processStream(Request httpRequest, StreamCallback callback) throws IOException {
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "empty";
                throw new IOException("WarfaceGPT API error " + response.code() + ": " + errorBody);
            }

            StringBuilder fullContent = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body().byteStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (data.equals("[DONE]")) break;

                        try {
                            StreamChunk chunk = objectMapper.readValue(data, StreamChunk.class);
                            if (chunk.choices != null && !chunk.choices.isEmpty()) {
                                String content = chunk.choices.get(0).delta != null
                                    ? chunk.choices.get(0).delta.content : null;
                                if (content != null && !content.isEmpty()) {
                                    fullContent.append(content);
                                    callback.onPartialResponse(content);
                                }
                            }
                        } catch (Exception e) {
                            // Skip malformed chunks
                        }
                    }
                }
            }

            String result = fullContent.toString();
            callback.onComplete(result);
            return result;
        }
    }

    // === Jackson Models ===

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIRequest {
        public String model;
        public List<OpenAIMessage> messages;
        public int max_tokens;
        public double temperature;
        public boolean stream;

        // Jackson needs the Java field names for serialization
        public int getMaxTokens() { return max_tokens; }
        public double getTemperature() { return temperature; }
        public boolean getStream() { return stream; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIMessage {
        public String role;
        public String content;

        public OpenAIMessage() { }
        public OpenAIMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenAIResponse {
        public List<Choice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        public Message message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        public String content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamChunk {
        public List<StreamChoice> choices;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamChoice {
        public StreamDelta delta;
        @JsonProperty("finish_reason")
        public String finishReason;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StreamDelta {
        public String content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelsResponse {
        public List<ModelInfo> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelInfo {
        public String id;
    }
}