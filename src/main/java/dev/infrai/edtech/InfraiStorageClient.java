package dev.infrai.edtech;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiStorageClient {
    public static final String CANONICAL_API = "infrai.storage.bucket.create";

    private final StorageConfig config;
    private final HttpClient http;

    public InfraiStorageClient(StorageConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiStorageClient(StorageConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public void createBucket(String name) {
        call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    public PresignedUpload presignCoursePut(String bucket, String key, String contentType,
                                             long maxBytes, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("op", "put");
        body.put("expires_seconds", 600);
        body.put("content_type", contentType);
        body.put("max_bytes", maxBytes);
        body.put("idempotency_key", idempotencyKey);
        Map<String, Object> data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + segment(key), body);
        return new PresignedUpload((String) data.get("url"), "PUT", bucket, key);
    }

    public boolean objectExists(String bucket, String key) {
        Map<String, Object> data = call("GET", "/v1/storage/object/head/" + segment(bucket) + "/" + segment(key), null);
        return Boolean.TRUE.equals(data.get("found"));
    }

    public List<Map<String, Object>> listObjects(String bucket) {
        Map<String, Object> data = call("GET", "/v1/storage/object/list/" + segment(bucket), null);
        Object items = data.get("items");
        if (!(items instanceof List<?> values)) {
            throw new InfraiException("INVALID_RESPONSE", "Storage response did not contain items", 502);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked") Map<String, Object> item = (Map<String, Object>) map;
                result.add(item);
            }
        }
        return List.copyOf(result);
    }

    private Map<String, Object> call(String method, String path, Map<String, Object> body) {
        String jsonBody = body == null ? null : Json.write(body);
        for (int attempt = 0; ; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (jsonBody != null) {
                request.header("Content-Type", "application/json");
            }
            request.method(method, jsonBody == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonBody));
            HttpResponse<String> response;
            try {
                response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException exception) {
                throw new InfraiException("TRANSPORT_ERROR", exception.getMessage(), 503);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", "Request interrupted", 503);
            }

            Map<String, Object> envelope = Json.object(response.body());
            if (response.statusCode() == 429 && attempt < config.maxRetries()) {
                sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = objectValue(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.getOrDefault("code", "REQUEST_REJECTED")),
                        String.valueOf(error.getOrDefault("message", "Request rejected")), response.statusCode());
            }
            return objectValue(envelope.get("data"));
        }
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try {
            return Math.max(0, Long.parseLong(retryAfter)) * 1_000;
        } catch (NumberFormatException ignored) {
            return 250L * (1L << attempt);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Retry interrupted", 503);
        }
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static Map<String, Object> objectValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new InfraiException("INVALID_RESPONSE", "Expected a JSON object", 502);
        }
        @SuppressWarnings("unchecked") Map<String, Object> result = (Map<String, Object>) map;
        return result;
    }

    public record PresignedUpload(String url, String method, String bucket, String key) {}

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        public InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }
}
