package example.fieldservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiGateway {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiGateway(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                         @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.key = key;
        if (key.isBlank()) throw new IllegalArgumentException("Set INFRAI_API_KEY");
    }

    public JsonNode sendOtp(String phone, String requestId) {
        return post("/v1/sms/otp", Map.of("to", phone), requestId);
    }

    public JsonNode verifyPhone(String phone, String code, String requestId) {
        return post("/v1/auth/phone/verify", Map.of("phone", phone, "code", code, "login", true), requestId);
    }

    private JsonNode post(String path, Map<String, Object> body, String requestId) {
        try {
            String payload = json.writeValueAsString(body);
            for (int attempt = 0; attempt < 4; attempt++) {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", requestId)
                    .timeout(Duration.ofSeconds(15))
                    .method("POST", HttpRequest.BodyPublishers.ofString(payload)).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (!envelope.path("ok").asBoolean(false)) {
                    if (response.statusCode() == 429 && attempt < 3) {
                        long seconds = response.headers().firstValue("Retry-After")
                            .map(value -> { try { return Long.parseLong(value); } catch (NumberFormatException e) { return 0L; } })
                            .orElse(0L);
                        Thread.sleep(Math.max(seconds * 1000, 250L << attempt));
                        continue;
                    }
                    JsonNode error = envelope.path("error");
                    throw new Rejection(response.statusCode(), error.path("code").asText("REQUEST_REJECTED"),
                                        error.path("message").asText("Request rejected"));
                }
                if (response.statusCode() >= 500) throw new IOException("Upstream response: " + response.statusCode());
                return envelope.path("data");
            }
            throw new IOException("Retry limit reached");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to complete request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request interrupted", e);
        }
    }

    public static class Rejection extends RuntimeException {
        public final int status;
        public final String code;
        public Rejection(int status, String code, String detail) {
            super(detail);
            this.status = status >= 400 && status < 500 ? status : 502;
            this.code = code;
        }
    }
}
