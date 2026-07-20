package com.warfacegpt.replugin.config;

import com.warfacegpt.replugin.service.WarfaceAPIClient;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Manages persistent configuration for WarfaceGPT RE Plugin.
 * Config stored in ~/.warfacegpt-re/config.properties
 * API key stored encrypted with XOR obfuscation for security at rest.
 */
public class ConfigurationManager {

    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".warfacegpt-re";
    private static final String CONFIG_FILE = "config.properties";

    private static final String API_KEY_PROPERTY = "api.key.encrypted";
    private static final String MODEL_PROPERTY = "api.model";
    private static final String MAX_TOKENS_PROPERTY = "api.max.tokens";
    private static final String TEMPERATURE_PROPERTY = "api.temperature";
    private static final String TIMEOUT_PROPERTY = "api.timeout.seconds";

    // XOR key for API key obfuscation
    private static final String XOR_KEY = "WarfaceGPT_RE_2026_S3cur3!x7z#k9m";

    private final Properties properties;
    private final Path configPath;

    public ConfigurationManager() {
        this.properties = new Properties();
        this.configPath = Paths.get(CONFIG_DIR, CONFIG_FILE);
        loadConfiguration();
    }

    private void loadConfiguration() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (Files.exists(configPath)) {
                try (InputStream input = Files.newInputStream(configPath)) {
                    properties.load(input);
                }
            } else {
                createDefaultConfiguration();
            }
        } catch (IOException e) {
            System.err.println("Failed to load configuration: " + e.getMessage());
            createDefaultConfiguration();
        }
    }

    private void createDefaultConfiguration() {
        properties.setProperty(MODEL_PROPERTY, WarfaceAPIClient.DEFAULT_MODEL);
        properties.setProperty(API_KEY_PROPERTY, "");
        properties.setProperty(MAX_TOKENS_PROPERTY, String.valueOf(WarfaceAPIClient.DEFAULT_MAX_TOKENS));
        properties.setProperty(TEMPERATURE_PROPERTY, String.valueOf(WarfaceAPIClient.DEFAULT_TEMPERATURE));
        properties.setProperty(TIMEOUT_PROPERTY, String.valueOf(WarfaceAPIClient.DEFAULT_TIMEOUT_SECONDS));
    }

    public void saveConfiguration() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            try (OutputStream output = Files.newOutputStream(configPath)) {
                properties.store(output, "WarfaceGPT RE Plugin Configuration");
            }
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }

    // === API Key (XOR encrypted at rest) ===

    public String getApiKey() {
        String encryptedKey = properties.getProperty(API_KEY_PROPERTY, "");
        if (encryptedKey.isEmpty()) return "";
        String decryptedKey = decrypt(encryptedKey);
        return decryptedKey != null ? decryptedKey : "";
    }

    public void setApiKey(String apiKey) {
        if (apiKey == null) apiKey = "";
        properties.setProperty(API_KEY_PROPERTY, encrypt(apiKey));
    }

    // === Model ===

    public String getModel() {
        return properties.getProperty(MODEL_PROPERTY, WarfaceAPIClient.DEFAULT_MODEL);
    }

    public void setModel(String model) {
        properties.setProperty(MODEL_PROPERTY, model != null ? model : WarfaceAPIClient.DEFAULT_MODEL);
    }

    // === Max Tokens ===

    public int getMaxTokens() {
        return Integer.parseInt(properties.getProperty(MAX_TOKENS_PROPERTY,
            String.valueOf(WarfaceAPIClient.DEFAULT_MAX_TOKENS)));
    }

    public void setMaxTokens(int maxTokens) {
        properties.setProperty(MAX_TOKENS_PROPERTY, String.valueOf(maxTokens));
    }

    // === Temperature ===

    public double getTemperature() {
        return Double.parseDouble(properties.getProperty(TEMPERATURE_PROPERTY,
            String.valueOf(WarfaceAPIClient.DEFAULT_TEMPERATURE)));
    }

    public void setTemperature(double temperature) {
        properties.setProperty(TEMPERATURE_PROPERTY, String.valueOf(temperature));
    }

    // === Timeout ===

    public int getTimeoutSeconds() {
        try {
            return Integer.parseInt(properties.getProperty(TIMEOUT_PROPERTY,
                String.valueOf(WarfaceAPIClient.DEFAULT_TIMEOUT_SECONDS)));
        } catch (NumberFormatException e) {
            return WarfaceAPIClient.DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        properties.setProperty(TIMEOUT_PROPERTY, String.valueOf(timeoutSeconds));
    }

    // === Config state ===

    public boolean isConfigured() {
        return !getApiKey().trim().isEmpty() && !getModel().trim().isEmpty();
    }

    public boolean configurationFileExists() {
        return Files.exists(configPath);
    }

    public String getConfigurationPath() {
        return configPath.toString();
    }

    // === XOR encryption ===

    private String encrypt(String input) {
        if (input == null || input.isEmpty()) return "";
        try {
            byte[] keyBytes = XOR_KEY.getBytes("UTF-8");
            byte[] inputBytes = input.getBytes("UTF-8");
            byte[] encrypted = new byte[inputBytes.length];
            for (int i = 0; i < inputBytes.length; i++) {
                encrypted[i] = (byte) (inputBytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : encrypted) {
                hex.append(String.format("%02x", b & 0xFF));
            }
            return hex.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private String decrypt(String encryptedHex) {
        if (encryptedHex == null || encryptedHex.isEmpty()) return "";
        try {
            byte[] encrypted = new byte[encryptedHex.length() / 2];
            for (int i = 0; i < encrypted.length; i++) {
                encrypted[i] = (byte) Integer.parseInt(encryptedHex.substring(i * 2, i * 2 + 2), 16);
            }
            byte[] keyBytes = XOR_KEY.getBytes("UTF-8");
            byte[] decrypted = new byte[encrypted.length];
            for (int i = 0; i < encrypted.length; i++) {
                decrypted[i] = (byte) (encrypted[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}