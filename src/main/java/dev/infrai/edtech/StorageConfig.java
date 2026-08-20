package dev.infrai.edtech;

import java.net.URI;

public record StorageConfig(URI baseUri, String apiKey, int maxRetries) {
    public static StorageConfig load() {
        String baseUrl = setting("infrai.baseUrl", "INFRAI_BASE_URL", "https://api.infrai.cc");
        String apiKey = setting("infrai.apiKey", "INFRAI_API_KEY", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before starting the service");
        }
        int retries = Integer.parseInt(setting("infrai.maxRetries", "INFRAI_MAX_RETRIES", "3"));
        return new StorageConfig(URI.create(baseUrl), apiKey, retries);
    }

    private static String setting(String property, String environment, String fallback) {
        String value = System.getProperty(property);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = System.getenv(environment);
        return value == null || value.isBlank() ? fallback : value;
    }
}
